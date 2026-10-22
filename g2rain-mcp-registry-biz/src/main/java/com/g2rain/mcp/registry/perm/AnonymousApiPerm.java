package com.g2rain.mcp.registry.perm;


import com.g2rain.basis.enums.BasisSyncerEnum;
import com.g2rain.basis.vo.SessionApiPermissionVo;
import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.model.Result;
import com.g2rain.common.syncer.AbstractMessageStorage;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Collections;
import com.g2rain.mcp.registry.client.AuthorityClient;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ANONYMOUS 会话的接口权限。
 * <p>
 * 按机构懒加载。{@code ANONYMOUS_PERM} 消息带着机构标识，收到后丢掉该机构的本地缓存，下次调用再回源。
 * 接入码是否已停用只由网关校验：工具调用来自已经过网关的对话请求，这里只看机构开通了哪些接口。
 * </p>
 *
 * @author alpha
 * @since 2026/10/22
 */
@Service
public class AnonymousApiPerm extends AbstractMessageStorage<Long, Long, Long> {

    private final AuthorityClient authorityClient;

    private final ConcurrentHashMap<Long, Set<Long>> apiIdsByOrgan = new ConcurrentHashMap<>();

    /**
     * @param authorityClient basis 授权 Feign
     */
    public AnonymousApiPerm(AuthorityClient authorityClient) {
        this.authorityClient = authorityClient;
    }

    /**
     * 机构是否开通了这个接口。缓存没有时向 basis 拉快照。回源失败抛出运行时异常，不把空结果写成“无权限”。
     *
     * @param organId 机构标识
     * @param apiId   {@code resource_api.id}
     * @return 有权限返回 {@code true}
     */
    public boolean hasApiPermission(Long organId, Long apiId) {
        if (Objects.isNull(organId) || organId <= 0 || Objects.isNull(apiId)) {
            return false;
        }
        return apiIds(organId).contains(apiId);
    }

    private Set<Long> apiIds(Long organId) {
        Set<Long> cached = apiIdsByOrgan.get(organId);
        if (Objects.nonNull(cached)) {
            return cached;
        }
        synchronized (this) {
            cached = apiIdsByOrgan.get(organId);
            if (Objects.nonNull(cached)) {
                return cached;
            }
            Result<SessionApiPermissionVo> result = authorityClient.getSessionApiPermissions(
                SessionType.ANONYMOUS.name(), organId);
            Asserts.isTrue(Objects.nonNull(result) && result.isSuccess() && Objects.nonNull(result.getData()),
                SystemErrorCode.SYSTEM_INTERNAL_ERROR, "basis session_api_permissions");
            SessionApiPermissionVo snapshot = result.getData();
            Set<Long> loaded = new HashSet<>();
            if (Collections.isNotEmpty(snapshot.getApiIds())) {
                snapshot.getApiIds().stream().filter(Objects::nonNull).forEach(loaded::add);
            }
            Set<Long> stored = Set.copyOf(loaded);
            apiIdsByOrgan.put(organId, stored);
            return stored;
        }
    }

    @Override
    protected @NonNull String dataSource() {
        return BasisSyncerEnum.ANONYMOUS_PERM.name();
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
        apiIdsByOrgan.remove(key);
    }

    @Override
    protected void delete(@NonNull Long key) {
        apiIdsByOrgan.remove(key);
    }

    @Override
    protected void update(@NonNull Long key, Long value) {
        apiIdsByOrgan.remove(key);
    }

    @Override
    protected Long get(@NonNull Long key) {
        return key;
    }
}
