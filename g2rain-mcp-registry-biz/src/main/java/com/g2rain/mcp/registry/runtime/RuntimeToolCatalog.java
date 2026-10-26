package com.g2rain.mcp.registry.runtime;


import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.PrincipalContextHolder;
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
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 租户（CUSTOMER）工具的运行时目录：按当前登录机构查库组装，服务按外部服务主键（{@code service_id}）匹配。
 * <p>
 * 扩展工具已由 {@link ExtensionToolLoader} 装入内存，不走这里。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Slf4j
@Component
public class RuntimeToolCatalog {

    private final McpToolProfileDao toolProfileDao;

    private final McpExternalServiceDao externalServiceDao;

    private final ToolOperationAssembler assembler;

    private final ObjectProvider<ToolRegistry> toolRegistry;

    public RuntimeToolCatalog(McpToolProfileDao toolProfileDao, McpExternalServiceDao externalServiceDao,
                              ToolOperationAssembler assembler, ObjectProvider<ToolRegistry> toolRegistry) {
        this.toolProfileDao = toolProfileDao;
        this.externalServiceDao = externalServiceDao;
        this.assembler = assembler;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 列出当前机构启用的租户工具。
     */
    public List<McpSchema.Tool> list() {
        Long organId = PrincipalContextHolder.getOrganId();
        if (Objects.isNull(organId)) {
            return List.of();
        }
        List<McpSchema.Tool> tools = new ArrayList<>();
        Map<Long, McpExternalServicePo> services = loadServiceMap(organId);
        for (McpToolProfilePo profile : listProfiles(organId, null)) {
            OpenApiOperation operation = operation(profile, services);
            if (Objects.isNull(operation) || Strings.isBlank(profile.getName())) {
                continue;
            }
            tools.add(toolRegistry.getObject().specification(profile.getName(), profile.getResourceType(), operation,
                profile.getInputSchema()).tool());
        }
        return tools;
    }

    /**
     * 按名称查找当前机构的租户工具。
     */
    public Optional<McpServerFeatures.SyncToolSpecification> find(String toolName) {
        if (Strings.isBlank(toolName)) {
            return Optional.empty();
        }
        Long organId = PrincipalContextHolder.getOrganId();
        if (Objects.isNull(organId)) {
            return Optional.empty();
        }
        McpToolProfilePo profile = first(listProfiles(organId, toolName));
        if (Objects.isNull(profile)) {
            return Optional.empty();
        }
        OpenApiOperation operation = operation(profile, loadServiceMap(organId));
        if (Objects.isNull(operation)) {
            return Optional.empty();
        }
        return Optional.of(toolRegistry.getObject().specification(profile.getName(), profile.getResourceType(), operation,
                profile.getInputSchema()));
    }

    private @Nullable OpenApiOperation operation(McpToolProfilePo profile, Map<Long, McpExternalServicePo> services) {
        McpExternalServicePo service = Objects.isNull(profile.getServiceId()) ? null : services.get(profile.getServiceId());
        if (Objects.isNull(service) || Strings.isBlank(service.getEndpoint())) {
            log.warn("工具缺少外部服务地址 name={} serviceId={}", profile.getName(), profile.getServiceId());
            return null;
        }

        return assembler.assemble(profile, service.getEndpoint());
    }

    private Map<Long, McpExternalServicePo> loadServiceMap(Long organId) {
        McpExternalServiceSelectDto select = new McpExternalServiceSelectDto();
        select.setResourceType(ResourceType.CUSTOMER.name());
        select.setStatus(ResourceStatus.ACTIVE.name());
        select.setOrganId(organId);
        List<McpExternalServicePo> services = externalServiceDao.selectListWithoutIsolation(select);
        if (Collections.isEmpty(services)) {
            return Map.of();
        }

        return services.stream()
            .collect(Collectors.toMap(McpExternalServicePo::getId, service -> service, (a, b) -> a, HashMap::new));
    }

    private List<McpToolProfilePo> listProfiles(Long organId, String toolName) {
        McpToolProfileSelectDto select = new McpToolProfileSelectDto();
        select.setResourceType(ResourceType.CUSTOMER.name());
        select.setStatus(ResourceStatus.ACTIVE.name());
        select.setOrganId(organId);
        if (Strings.isNotBlank(toolName)) {
            select.setName(toolName);
        }
        List<McpToolProfilePo> profiles = toolProfileDao.selectListWithoutIsolation(select);
        return Collections.isEmpty(profiles) ? List.of() : profiles;
    }

    private static <T> T first(List<T> rows) {
        return Collections.isEmpty(rows) ? null : rows.getFirst();
    }
}
