package com.g2rain.mcp.registry.perm;


import com.g2rain.basis.enums.BasisSyncerEnum;
import com.g2rain.basis.vo.BaseAuthorityApiVo;
import com.g2rain.common.model.Result;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Collections;
import com.g2rain.mcp.registry.client.AuthorityClient;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户会话的接口权限。
 * <p>
 * 按机构、用户、应用懒加载。{@code USER_PERM} 消息带着机构标识，收到后丢掉该机构下的本地缓存。
 * 回源失败不写缓存，下次请求再试。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Slf4j
@Service
public class UserApiPerm extends AbstractMessageStorage<Long, Long, Long> {

    private final AuthorityClient authorityClient;

    private final ConcurrentHashMap<Long, Map<Long, Map<Long, Map<Long, String>>>> permissions = new ConcurrentHashMap<>();

    /**
     * @param authorityClient basis 授权 Feign
     */
    public UserApiPerm(AuthorityClient authorityClient) {
        this.authorityClient = authorityClient;
    }

    /**
     * 读取用户对某个接口的授权状态。
     *
     * @param organId       机构标识
     * @param userId        用户标识
     * @param applicationId 应用标识
     * @param apiId         {@code resource_api.id}
     * @return 授权状态；没有这条权限或回源失败时返回 {@code null}
     */
    public String status(Long organId, Long userId, Long applicationId, Long apiId) {
        if (Objects.isNull(organId) || Objects.isNull(userId) || Objects.isNull(applicationId) || Objects.isNull(apiId)) {
            return null;
        }
        Map<Long, String> snapshot = snapshot(organId, userId, applicationId);
        if (Objects.isNull(snapshot)) {
            return null;
        }
        return snapshot.get(apiId);
    }

    private Map<Long, String> snapshot(Long organId, Long userId, Long applicationId) {
        Map<Long, String> cached = cached(organId, userId, applicationId);
        if (Objects.nonNull(cached)) {
            return cached;
        }
        synchronized (this) {
            cached = cached(organId, userId, applicationId);
            if (Objects.nonNull(cached)) {
                return cached;
            }
            Result<List<BaseAuthorityApiVo>> result = authorityClient.getApiPermissions(userId, null, applicationId);
            if (Objects.isNull(result) || !result.isSuccess()) {
                log.warn("加载用户接口权限失败 organId={} userId={} applicationId={}", organId, userId, applicationId);
                return null;
            }
            List<BaseAuthorityApiVo> rows = Objects.isNull(result.getData()) ? List.of() : result.getData();
            Map<Long, String> loaded = new HashMap<>();
            if (Collections.isNotEmpty(rows)) {
                for (BaseAuthorityApiVo row : rows) {
                    if (Objects.isNull(row) || Objects.isNull(row.getId())) {
                        continue;
                    }
                    loaded.put(row.getId(), row.getStatus());
                }
            }
            permissions.computeIfAbsent(organId, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(userId, key -> new ConcurrentHashMap<>())
                .put(applicationId, Map.copyOf(loaded));
            return loaded;
        }
    }

    private Map<Long, String> cached(Long organId, Long userId, Long applicationId) {
        Map<Long, Map<Long, Map<Long, String>>> users = permissions.get(organId);
        if (Objects.isNull(users)) {
            return null;
        }
        Map<Long, Map<Long, String>> apps = users.get(userId);
        if (Objects.isNull(apps)) {
            return null;
        }
        return apps.get(applicationId);
    }

    @Override
    protected @NonNull String dataSource() {
        return BasisSyncerEnum.USER_PERM.name();
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
        permissions.remove(key);
    }

    @Override
    protected void delete(@NonNull Long key) {
        permissions.remove(key);
    }

    @Override
    protected void update(@NonNull Long key, Long value) {
        permissions.remove(key);
    }

    @Override
    protected Long get(@NonNull Long key) {
        return key;
    }
}
