package com.g2rain.mcp.registry.runtime;

import com.g2rain.basis.vo.ServiceRegistryVo;
import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.StringRedisHelper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务注册缓存：按服务注册主键（即原生工具的 {@code service_id}）取调用基址与名称。
 * <p>
 * 调用基址就是 {@code service_registry.endpoint}（如 {@code lb://g2rain-basis}），不拼 routePrefix。
 * Redis 哈希保存同一份信息，服务注册变更时改对应字段；Redis 写失败只警告，内存仍以本次消息为准。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Slf4j
@Component
public class ServiceEndpointCache {

    static final String REDIS_KEY = "mcp:service-registry";

    private static final JsonCodec JSON = JsonCodecFactory.instance();

    private final StringRedisHelper redisHelper;

    private final ConcurrentHashMap<Long, ServiceRegistryVo> byId = new ConcurrentHashMap<>();

    /**
     * @param redisHelper 字符串 Redis 操作
     */
    public ServiceEndpointCache(StringRedisHelper redisHelper) {
        this.redisHelper = redisHelper;
    }

    /**
     * 用全量服务注册替换内存和 Redis。
     *
     * @param registries basis 返回的服务注册
     */
    public void replaceAll(@Nullable List<ServiceRegistryVo> registries) {
        byId.clear();
        deleteRedis();
        if (Objects.isNull(registries)) {
            return;
        }

        for (ServiceRegistryVo registry : registries) {
            upsert(registry);
        }
    }

    /**
     * 新增或更新一条服务注册。
     *
     * @param registry 消息里的服务注册
     */
    public void upsert(@Nullable ServiceRegistryVo registry) {
        if (Objects.isNull(registry) || Objects.isNull(registry.getId())) {
            return;
        }

        byId.put(registry.getId(), registry);
        writeField(registry);
    }

    /**
     * 按主键删除服务注册。
     *
     * @param id 服务注册主键
     */
    public void remove(Long id) {
        if (Objects.nonNull(byId.remove(id))) {
            deleteField(id);
        }
    }

    /**
     * @param id 服务注册主键
     * @return 服务注册；未注册时返回 {@code null}
     */
    public @Nullable ServiceRegistryVo get(@Nullable Long id) {
        return Objects.isNull(id) ? null : byId.get(id);
    }

    /**
     * @param id 服务注册主键
     * @return 调用基址；未注册或没有地址时返回 {@code null}
     */
    public @Nullable String endpoint(@Nullable Long id) {
        ServiceRegistryVo registry = get(id);
        return Objects.isNull(registry) || Strings.isBlank(registry.getEndpoint()) ? null : registry.getEndpoint();
    }

    /**
     * @param id 服务注册主键
     * @return 服务名称；没有名称时用服务编码，未注册时返回 {@code null}
     */
    public @Nullable String name(@Nullable Long id) {
        ServiceRegistryVo registry = get(id);
        if (Objects.isNull(registry)) {
            return null;
        }

        return Strings.isNotBlank(registry.getName()) ? registry.getName() : registry.getServiceCode();
    }

    private void writeField(ServiceRegistryVo registry) {
        try {
            StoredEndpoint stored = new StoredEndpoint();
            stored.setServiceCode(registry.getServiceCode());
            stored.setName(registry.getName());
            stored.setEndpoint(registry.getEndpoint());
            redisHelper.hSet(REDIS_KEY, String.valueOf(registry.getId()), JSON.obj2str(stored));
        } catch (RuntimeException ex) {
            log.warn("更新服务注册 Redis 缓存失败 id={}", registry.getId(), ex);
        }
    }

    private void deleteField(Long id) {
        try {
            redisHelper.hDel(REDIS_KEY, String.valueOf(id));
        } catch (RuntimeException ex) {
            log.warn("删除服务注册 Redis 缓存失败 id={}", id, ex);
        }
    }

    private void deleteRedis() {
        try {
            redisHelper.delete(REDIS_KEY);
        } catch (RuntimeException ex) {
            log.warn("清空服务注册 Redis 缓存失败", ex);
        }
    }

    /**
     * Redis 里保存的服务信息。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class StoredEndpoint {
        private String serviceCode;
        private String name;
        private String endpoint;
    }
}
