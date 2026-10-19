package com.g2rain.mcp.registry.runtime;


import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.dao.McpExternalServiceDao;
import com.g2rain.mcp.registry.dao.McpToolProfileDao;
import com.g2rain.mcp.registry.dao.po.McpExternalServicePo;
import com.g2rain.mcp.registry.dao.po.McpToolProfilePo;
import com.g2rain.mcp.registry.dto.McpExternalServiceSelectDto;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import com.g2rain.mcp.registry.enums.ResourceStatus;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把启用的扩展（EXTENSION）工具全量装进内存；与 BUILT_IN 同属共享工具。
 */
@Slf4j
@Component
public class ExtensionToolLoader {

    private final McpToolProfileDao toolProfileDao;

    private final McpExternalServiceDao externalServiceDao;

    private final ToolOperationAssembler assembler;

    private final ToolRegistry toolRegistry;

    private final ConcurrentHashMap<String, Set<Long>> profileIdsByService = new ConcurrentHashMap<>();

    public ExtensionToolLoader(McpToolProfileDao toolProfileDao, McpExternalServiceDao externalServiceDao,
                               ToolOperationAssembler assembler, ToolRegistry toolRegistry) {
        this.toolProfileDao = toolProfileDao;
        this.externalServiceDao = externalServiceDao;
        this.assembler = assembler;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 清空后装载全部 ACTIVE 扩展工具。
     */
    public void loadAll() {
        dropAll();
        Map<String, McpExternalServicePo> services = loadActiveServices();
        int loaded = 0;
        for (McpToolProfilePo profile : list(null)) {
            if (register(profile, services)) {
                loaded++;
            }
        }
        log.info("扩展工具已装入内存 count={}", loaded);
    }

    /**
     * 按服务编码重装：先组好转再卸旧装新。
     */
    public void reload(String serviceCode) {
        if (Strings.isBlank(serviceCode)) {
            return;
        }
        Map<String, McpExternalServicePo> services = loadActiveServices(serviceCode);
        List<Prepared> prepared = new ArrayList<>();
        for (McpToolProfilePo profile : list(serviceCode)) {
            Prepared item = prepare(profile, services);
            if (Objects.nonNull(item)) {
                prepared.add(item);
            }
        }
        synchronized (this) {
            drop(serviceCode);
            for (Prepared item : prepared) {
                commit(item);
            }
        }
    }

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

    private boolean register(McpToolProfilePo profile, Map<String, McpExternalServicePo> services) {
        Prepared prepared = prepare(profile, services);
        if (Objects.isNull(prepared)) {
            return false;
        }
        commit(prepared);
        return true;
    }

    private Prepared prepare(McpToolProfilePo profile, Map<String, McpExternalServicePo> services) {
        if (Objects.isNull(profile.getId()) || Strings.isBlank(profile.getName()) || Strings.isBlank(profile.getServiceCode())) {
            return null;
        }
        McpExternalServicePo service = services.get(profile.getServiceCode());
        if (Objects.isNull(service) || Strings.isBlank(service.getEndpoint())) {
            log.warn("扩展工具缺少服务地址 name={} serviceCode={}", profile.getName(), profile.getServiceCode());
            return null;
        }
        OpenApiOperation operation = assembler.assemble(profile, service.getEndpoint(), service.getContextPath());
        if (Objects.isNull(operation)) {
            return null;
        }
        return new Prepared(profile.getId(), profile.getName(), profile.getSourceId(), profile.getServiceCode(), operation);
    }

    private void commit(Prepared prepared) {
        toolRegistry.register(prepared.profileId(), prepared.name(), prepared.sourceId(),
            ResourceType.EXTENSION.name(), prepared.operation());
        profileIdsByService.computeIfAbsent(prepared.serviceCode(), key -> ConcurrentHashMap.newKeySet())
            .add(prepared.profileId());
    }

    private List<McpToolProfilePo> list(String serviceCode) {
        McpToolProfileSelectDto select = new McpToolProfileSelectDto();
        select.setResourceType(ResourceType.EXTENSION.name());
        select.setStatus(ResourceStatus.ACTIVE.name());
        if (Strings.isNotBlank(serviceCode)) {
            select.setServiceCode(serviceCode);
        }
        List<McpToolProfilePo> profiles = toolProfileDao.selectListWithoutIsolation(select);
        return Collections.isEmpty(profiles) ? List.of() : profiles;
    }

    private Map<String, McpExternalServicePo> loadActiveServices() {
        return loadActiveServices(null);
    }

    private Map<String, McpExternalServicePo> loadActiveServices(String serviceCode) {
        McpExternalServiceSelectDto select = new McpExternalServiceSelectDto();
        select.setResourceType(ResourceType.EXTENSION.name());
        select.setStatus(ResourceStatus.ACTIVE.name());
        if (Strings.isNotBlank(serviceCode)) {
            select.setCode(serviceCode);
        }
        List<McpExternalServicePo> rows = externalServiceDao.selectListWithoutIsolation(select);
        Map<String, McpExternalServicePo> byCode = new HashMap<>();
        if (Collections.isEmpty(rows)) {
            return byCode;
        }
        for (McpExternalServicePo row : rows) {
            if (Strings.isNotBlank(row.getCode())) {
                byCode.put(row.getCode(), row);
            }
        }
        return byCode;
    }

    private record Prepared(Long profileId, String name, Long sourceId, String serviceCode, OpenApiOperation operation) {
    }
}
