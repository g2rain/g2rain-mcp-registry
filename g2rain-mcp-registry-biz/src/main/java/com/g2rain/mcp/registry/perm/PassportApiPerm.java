package com.g2rain.mcp.registry.perm;


import com.g2rain.basis.enums.BasisSyncerEnum;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.model.Result;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Collections;
import com.g2rain.mcp.registry.client.AuthorityClient;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号会话的接口权限。
 * <p>
 * 启动时拉全量 {@code resource_api.id}。{@code PASSPORT_PERM} 消息按接口标识增减本地集合。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Slf4j
@Service
public class PassportApiPerm extends AbstractMessageStorage<Long, Long, Long> {

    private final AuthorityClient authorityClient;

    private final Set<Long> apiIds = ConcurrentHashMap.newKeySet();

    /**
     * @param authorityClient basis 授权 Feign
     */
    public PassportApiPerm(AuthorityClient authorityClient) {
        this.authorityClient = authorityClient;
    }

    /**
     * 启动时装入账号共用的接口权限。
     */
    @Override
    public void load() {
        apiIds.clear();
        Result<List<Long>> result = authorityClient.getPassportApiPermissions();
        Asserts.isTrue(Objects.nonNull(result) && result.isSuccess(),
            SystemErrorCode.SYSTEM_INTERNAL_ERROR, "basis passport_api_permissions");
        List<Long> rows = result.getData();
        if (Collections.isNotEmpty(rows)) {
            rows.stream().filter(Objects::nonNull).forEach(apiIds::add);
        }
        log.info("账号接口权限已装入 count={}", apiIds.size());
    }

    /**
     * 账号会话是否可以调用这个接口。
     *
     * @param apiId {@code resource_api.id}
     * @return 有权限返回 {@code true}
     */
    public boolean hasApiPermission(Long apiId) {
        return Objects.nonNull(apiId) && apiIds.contains(apiId);
    }

    @Override
    protected @NonNull String dataSource() {
        return BasisSyncerEnum.PASSPORT_PERM.name();
    }

    @Override
    protected @NonNull Class<Long> getValueType() {
        return Long.class;
    }

    @Override
    protected @NonNull Long getKey(@NonNull Long value) {
        return value;
    }

    @Override
    protected void create(@NonNull Long key, Long value) {
        apiIds.add(key);
    }

    @Override
    protected void delete(@NonNull Long key) {
        apiIds.remove(key);
    }

    @Override
    protected void update(@NonNull Long key, Long value) {
        apiIds.add(key);
    }

    @Override
    protected Long get(@NonNull Long key) {
        return key;
    }
}
