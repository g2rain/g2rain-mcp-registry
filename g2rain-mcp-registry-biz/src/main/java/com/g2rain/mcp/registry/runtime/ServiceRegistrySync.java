package com.g2rain.mcp.registry.runtime;


import com.g2rain.basis.dto.ServiceRegistrySelectDto;
import com.g2rain.basis.enums.BasisSyncerEnum;
import com.g2rain.basis.vo.ServiceRegistryVo;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.model.Result;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.client.ServiceRegistryClient;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 接收服务注册的 cache-sync 消息。
 * <p>
 * 启动时拉全量并装入内置工具。新增、修改时改内存和 Redis，再重装这个服务的内置工具。删除时卸下工具并删掉 Redis 字段。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Slf4j
@Service
public class ServiceRegistrySync extends AbstractMessageStorage<Long, ServiceRegistryVo, String> {

    private final ServiceRegistryClient serviceRegistryClient;

    private final ServiceEndpointCache endpoints;

    private final BuiltInToolLoader builtInToolLoader;

    private final ExtensionToolLoader extensionToolLoader;

    /**
     * @param serviceRegistryClient basis 服务注册 Feign
     * @param endpoints             服务地址缓存
     * @param builtInToolLoader     内置工具装载
     * @param extensionToolLoader   扩展工具装载
     */
    public ServiceRegistrySync(ServiceRegistryClient serviceRegistryClient, ServiceEndpointCache endpoints,
                               BuiltInToolLoader builtInToolLoader, ExtensionToolLoader extensionToolLoader) {
        this.serviceRegistryClient = serviceRegistryClient;
        this.endpoints = endpoints;
        this.builtInToolLoader = builtInToolLoader;
        this.extensionToolLoader = extensionToolLoader;
    }

    /**
     * 启动装载：服务注册进内存和 Redis，再装全部内置工具与扩展工具。
     */
    @Override
    public void load() {
        Result<List<ServiceRegistryVo>> result = serviceRegistryClient.selectList(new ServiceRegistrySelectDto());
        Asserts.isTrue(Objects.nonNull(result) && result.isSuccess(),
            SystemErrorCode.SYSTEM_INTERNAL_ERROR, "basis service_registry");
        List<ServiceRegistryVo> registries = Objects.isNull(result.getData()) ? List.of() : result.getData();
        endpoints.replaceAll(registries);
        builtInToolLoader.loadAll();
        extensionToolLoader.loadAll();
    }

    @Override
    protected @NonNull String dataSource() {
        return BasisSyncerEnum.INTERNAL_ROUTE.name();
    }

    @Override
    protected @NonNull Class<ServiceRegistryVo> getValueType() {
        return ServiceRegistryVo.class;
    }

    @Override
    protected @NonNull Long getKey(@NonNull ServiceRegistryVo value) {
        return value.getId();
    }

    @Override
    protected void create(@NonNull Long key, ServiceRegistryVo value) {
        apply(value);
    }

    @Override
    protected void update(@NonNull Long key, ServiceRegistryVo value) {
        apply(value);
    }

    @Override
    protected void delete(@NonNull Long key) {
        String serviceCode = endpoints.remove(key);
        if (Strings.isNotBlank(serviceCode)) {
            builtInToolLoader.drop(serviceCode);
        }
    }

    @Override
    protected String get(@NonNull Long key) {
        return null;
    }

    private void apply(ServiceRegistryVo value) {
        if (Objects.isNull(value) || Strings.isBlank(value.getServiceCode())) {
            return;
        }
        endpoints.upsert(value);
        builtInToolLoader.reload(value.getServiceCode());
        log.info("服务注册已同步 serviceCode={}", value.getServiceCode());
    }
}
