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
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把启用的扩展（EXTENSION）工具全量装进内存；与 NATIVE 同属共享工具。
 * <p>
 * 按外部服务主键（{@code service_id}）分组；服务停用时该服务的工具不装载。
 * </p>
 */
@Slf4j
@Component
public class ExtensionToolLoader {

    private final McpToolProfileDao toolProfileDao;

    private final McpExternalServiceDao externalServiceDao;

    private final ToolOperationAssembler assembler;

    private final ToolRegistry toolRegistry;

    private final ConcurrentHashMap<Long, Set<Long>> profileIdsByService = new ConcurrentHashMap<>();

    public ExtensionToolLoader(McpToolProfileDao toolProfileDao, McpExternalServiceDao externalServiceDao,
                               ToolOperationAssembler assembler, ToolRegistry toolRegistry) {
        this.toolProfileDao = toolProfileDao;
        this.externalServiceDao = externalServiceDao;
        this.assembler = assembler;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 卸下全部扩展工具后重新装载。
     */
    public void loadAll() {
        dropAll();
        Map<Long, McpExternalServicePo> services = loadActiveServices(null);
        int loaded = 0;
        for (McpToolProfilePo profile : list(null)) {
            if (register(profile, services)) {
                loaded++;
            }
        }
        log.info("扩展工具已装入内存 count={}", loaded);
    }

    /**
     * 重装一个服务的工具；服务停用时只卸下。
     *
     * @param serviceId 外部服务主键
     */
    public void reload(@Nullable Long serviceId) {
        if (Objects.isNull(serviceId)) {
            return;
        }

        Map<Long, McpExternalServicePo> services = loadActiveServices(serviceId);
        List<Prepared> prepared = new ArrayList<>();
        for (McpToolProfilePo profile : list(serviceId)) {
            Prepared item = prepare(profile, services);
            if (Objects.nonNull(item)) {
                prepared.add(item);
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
     * @param serviceId 外部服务主键
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

    private boolean register(McpToolProfilePo profile, Map<Long, McpExternalServicePo> services) {
        Prepared prepared = prepare(profile, services);
        if (Objects.isNull(prepared)) {
            return false;
        }

        commit(prepared);
        return true;
    }

    private @Nullable Prepared prepare(McpToolProfilePo profile, Map<Long, McpExternalServicePo> services) {
        if (Objects.isNull(profile.getId()) || Strings.isBlank(profile.getName()) || Objects.isNull(profile.getServiceId())) {
            return null;
        }

        McpExternalServicePo service = services.get(profile.getServiceId());
        if (Objects.isNull(service) || Strings.isBlank(service.getEndpoint())) {
            log.warn("扩展工具缺少服务地址 name={} serviceId={}", profile.getName(), profile.getServiceId());
            return null;
        }

        OpenApiOperation operation = assembler.assemble(profile, service.getEndpoint());
        if (Objects.isNull(operation)) {
            return null;
        }

        return new Prepared(profile.getId(), profile.getName(), profile.getSourceId(), profile.getServiceId(),
            operation, profile.getInputSchema());
    }

    private void commit(Prepared prepared) {
        toolRegistry.register(prepared.profileId(), prepared.name(), prepared.sourceId(),
            ResourceType.EXTENSION.name(), prepared.operation(), prepared.inputSchema());
        profileIdsByService.computeIfAbsent(prepared.serviceId(), key -> ConcurrentHashMap.newKeySet())
            .add(prepared.profileId());
    }

    private List<McpToolProfilePo> list(@Nullable Long serviceId) {
        McpToolProfileSelectDto select = new McpToolProfileSelectDto();
        select.setResourceType(ResourceType.EXTENSION.name());
        select.setStatus(ResourceStatus.ACTIVE.name());
        select.setServiceId(serviceId);
        List<McpToolProfilePo> profiles = toolProfileDao.selectListWithoutIsolation(select);
        return Collections.isEmpty(profiles) ? List.of() : profiles;
    }

    private Map<Long, McpExternalServicePo> loadActiveServices(@Nullable Long serviceId) {
        McpExternalServiceSelectDto select = new McpExternalServiceSelectDto();
        select.setId(serviceId);
        select.setResourceType(ResourceType.EXTENSION.name());
        select.setStatus(ResourceStatus.ACTIVE.name());
        List<McpExternalServicePo> rows = externalServiceDao.selectListWithoutIsolation(select);
        Map<Long, McpExternalServicePo> byId = new HashMap<>();
        if (Collections.isEmpty(rows)) {
            return byId;
        }

        for (McpExternalServicePo row : rows) {
            byId.put(row.getId(), row);
        }
        return byId;
    }

    private record Prepared(Long profileId, String name, @Nullable Long sourceId, Long serviceId,
                            OpenApiOperation operation, String inputSchema) {
    }
}
