package com.g2rain.mcp.registry.runtime;

import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.dao.McpToolProfileDao;
import com.g2rain.mcp.registry.dao.po.McpToolProfilePo;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import com.g2rain.mcp.registry.enums.ResourceStatus;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原生工具（NATIVE）装载器：按服务注册主键（{@code service_id}）分组装入 {@link ToolRegistry}。
 * <p>
 * 调用基址取 {@link ServiceEndpointCache}；服务未注册或没有地址时该服务的工具不装载。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Slf4j
@Component
public class NativeToolLoader {

    private final McpToolProfileDao toolProfileDao;

    private final ServiceEndpointCache endpoints;

    private final ToolOperationAssembler assembler;

    private final ToolRegistry toolRegistry;

    private final ConcurrentHashMap<Long, Set<Long>> profileIdsByService = new ConcurrentHashMap<>();

    public NativeToolLoader(McpToolProfileDao toolProfileDao, ServiceEndpointCache endpoints,
                            ToolOperationAssembler assembler, ToolRegistry toolRegistry) {
        this.toolProfileDao = toolProfileDao;
        this.endpoints = endpoints;
        this.assembler = assembler;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 卸下全部原生工具后重新装载。
     */
    public void loadAll() {
        dropAll();
        int loaded = 0;
        for (McpToolProfilePo profile : list(null)) {
            if (register(profile)) {
                loaded++;
            }
        }
        log.info("原生工具已装入内存 count={}", loaded);
    }

    /**
     * 重装一个服务的工具；服务未注册时只卸下。
     *
     * @param serviceId 服务注册主键
     */
    public void reload(@Nullable Long serviceId) {
        if (Objects.isNull(serviceId)) {
            return;
        }

        List<Prepared> prepared = new ArrayList<>();
        if (Objects.nonNull(endpoints.endpoint(serviceId))) {
            for (McpToolProfilePo profile : list(serviceId)) {
                Prepared item = prepare(profile);
                if (Objects.nonNull(item)) {
                    prepared.add(item);
                }
            }
        }

        synchronized (this) {
            drop(serviceId);
            for (Prepared item : prepared) {
                commit(item);
            }
        }
    }

    /**
     * 卸下一个服务的工具。
     *
     * @param serviceId 服务注册主键
     */
    public void drop(Long serviceId) {
        Set<Long> profileIds = profileIdsByService.remove(serviceId);
        if (Objects.isNull(profileIds)) {
            return;
        }

        for (Long profileId : profileIds) {
            toolRegistry.remove(profileId);
        }
    }

    private void dropAll() {
        for (Long serviceId : Set.copyOf(profileIdsByService.keySet())) {
            drop(serviceId);
        }
    }

    private boolean register(McpToolProfilePo profile) {
        Prepared prepared = prepare(profile);
        if (Objects.isNull(prepared)) {
            return false;
        }

        commit(prepared);
        return true;
    }

    private @Nullable Prepared prepare(McpToolProfilePo profile) {
        if (Objects.isNull(profile.getId()) || Strings.isBlank(profile.getName()) || Objects.isNull(profile.getServiceId())) {
            return null;
        }

        String endpoint = endpoints.endpoint(profile.getServiceId());
        if (Objects.isNull(endpoint)) {
            log.warn("原生工具缺少服务地址 name={} serviceId={}", profile.getName(), profile.getServiceId());
            return null;
        }

        OpenApiOperation operation = assembler.assemble(profile, endpoint);
        if (Objects.isNull(operation)) {
            return null;
        }

        return new Prepared(profile.getId(), profile.getName(), profile.getSourceId(), profile.getServiceId(),
            operation, profile.getInputSchema());
    }

    private void commit(Prepared prepared) {
        toolRegistry.register(prepared.profileId(), prepared.name(), prepared.sourceId(),
            ResourceType.NATIVE.name(), prepared.operation(), prepared.inputSchema());
        profileIdsByService.computeIfAbsent(prepared.serviceId(), key -> ConcurrentHashMap.newKeySet())
            .add(prepared.profileId());
    }

    private List<McpToolProfilePo> list(@Nullable Long serviceId) {
        McpToolProfileSelectDto select = new McpToolProfileSelectDto();
        select.setResourceType(ResourceType.NATIVE.name());
        select.setStatus(ResourceStatus.ACTIVE.name());
        select.setServiceId(serviceId);
        List<McpToolProfilePo> profiles = toolProfileDao.selectListWithoutIsolation(select);
        return Collections.isEmpty(profiles) ? List.of() : profiles;
    }

    private record Prepared(Long profileId, String name, @Nullable Long sourceId, Long serviceId,
                            OpenApiOperation operation, String inputSchema) {
    }
}
