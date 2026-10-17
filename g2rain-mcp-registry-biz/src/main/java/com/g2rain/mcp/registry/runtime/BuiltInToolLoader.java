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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把启用的内置工具装进内存。
 * <p>
 * 档案来自 {@code mcp_tool_profile}，调用地址来自 {@link ServiceEndpointCache}。
 * 服务注册变更后按服务编码重装这一批工具。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Slf4j
@Component
public class BuiltInToolLoader {

    private final McpToolProfileDao toolProfileDao;

    private final ServiceEndpointCache endpoints;

    private final ToolOperationAssembler assembler;

    private final ToolRegistry toolRegistry;

    private final ConcurrentHashMap<String, Set<Long>> profileIdsByService = new ConcurrentHashMap<>();

    public BuiltInToolLoader(McpToolProfileDao toolProfileDao, ServiceEndpointCache endpoints,
                             ToolOperationAssembler assembler, ToolRegistry toolRegistry) {
        this.toolProfileDao = toolProfileDao;
        this.endpoints = endpoints;
        this.assembler = assembler;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 清空后重新装载全部启用的内置工具。
     */
    public void loadAll() {
        dropAll();
        List<McpToolProfilePo> profiles = list(null);
        int loaded = 0;
        for (McpToolProfilePo profile : profiles) {
            if (register(profile)) {
                loaded++;
            }
        }
        log.info("内置工具已装入内存 count={}", loaded);
    }

    /**
     * 按服务编码重装：先组好转再卸旧装新，缩短不可用窗口。
     *
     * @param serviceCode 服务编码
     */
    public void reload(String serviceCode) {
        if (Strings.isBlank(serviceCode)) {
            return;
        }
        List<Prepared> prepared = new ArrayList<>();
        if (Objects.nonNull(endpoints.endpoint(serviceCode))) {
            for (McpToolProfilePo profile : list(serviceCode)) {
                Prepared item = prepare(profile);
                if (Objects.nonNull(item)) {
                    prepared.add(item);
                }
            }
        }
        synchronized (this) {
            drop(serviceCode);
            for (Prepared item : prepared) {
                commit(item);
            }
        }
    }

    /**
     * 卸下某个服务已装入内存的内置工具。
     *
     * @param serviceCode 服务编码
     */
    public void drop(String serviceCode) {
        Set<Long> profileIds = profileIdsByService.remove(serviceCode);
        if (Objects.isNull(profileIds)) {
            return;
        }
        for (Long profileId : profileIds) {
            toolRegistry.remove(profileId);
        }
    }

    private void dropAll() {
        for (String serviceCode : Set.copyOf(profileIdsByService.keySet())) {
            drop(serviceCode);
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

    private Prepared prepare(McpToolProfilePo profile) {
        if (Objects.isNull(profile.getId()) || Strings.isBlank(profile.getName()) || Strings.isBlank(profile.getServiceCode())) {
            return null;
        }
        ServiceEndpointCache.Endpoint endpoint = endpoints.endpoint(profile.getServiceCode());
        if (Objects.isNull(endpoint)) {
            log.warn("内置工具缺少服务地址 name={} serviceCode={}", profile.getName(), profile.getServiceCode());
            return null;
        }
        OpenApiOperation operation = assembler.assemble(profile, endpoint.endpoint(), endpoint.contextPath());
        if (Objects.isNull(operation)) {
            return null;
        }
        return new Prepared(profile.getId(), profile.getName(), profile.getSourceId(), profile.getServiceCode(), operation);
    }

    private void commit(Prepared prepared) {
        toolRegistry.register(prepared.profileId(), prepared.name(), prepared.sourceId(),
            ResourceType.BUILT_IN.name(), prepared.operation());
        profileIdsByService.computeIfAbsent(prepared.serviceCode(), key -> ConcurrentHashMap.newKeySet())
            .add(prepared.profileId());
    }

    private List<McpToolProfilePo> list(String serviceCode) {
        McpToolProfileSelectDto select = new McpToolProfileSelectDto();
        select.setResourceType(ResourceType.BUILT_IN.name());
        select.setStatus(ResourceStatus.ACTIVE.name());
        if (Strings.isNotBlank(serviceCode)) {
            select.setServiceCode(serviceCode);
        }
        List<McpToolProfilePo> profiles = toolProfileDao.selectListWithoutIsolation(select);
        return Collections.isEmpty(profiles) ? List.of() : profiles;
    }

    private record Prepared(Long profileId, String name, Long sourceId, String serviceCode, OpenApiOperation operation) {
    }
}
