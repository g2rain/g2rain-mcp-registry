package com.g2rain.mcp.registry.tool;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.context.McpRequestContext;
import com.g2rain.mcp.registry.enums.McpErrorCode;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.perm.NativeApiPermission;
import com.g2rain.mcp.registry.runtime.RuntimeToolCatalog;
import com.g2rain.mcp.registry.schema.InputSchemaComposer;
import com.g2rain.mcp.registry.tool.filter.RegisteredTool;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Tool 注册表, {@code tools/list} 与 {@code tools/call} 共用.
 * <p>
 * 内存缓存 {@link ResolvedTool}，按 tool name（{@code byCode}）定位。
 * </p>
 *
 * @author alpha
 * @since 2026/6/29
 */
@Slf4j
@Component
@NullMarked
@RequiredArgsConstructor
public class ToolRegistry {

    private final ConcurrentHashMap<String, ResolvedTool> byCode = new ConcurrentHashMap<>();

    private final InputSchemaComposer inputSchemaComposer;
    private final ToolHandler toolHandler;
    private final ToolSpecBuilder toolSpecBuilder;
    private final RuntimeToolCatalog runtimeToolCatalog;
    private final NativeApiPermission nativeApiPermission;

    /**
     * 写入或热更新一条 {@link ResolvedTool}.
     */
    public void put(ResolvedTool resolvedTool) {
        byCode.values().stream()
            .filter(existing -> existing.profileId().equals(resolvedTool.profileId()))
            .findAny()
            .ifPresent(existing -> byCode.remove(existing.code()));

        byCode.put(resolvedTool.code(), resolvedTool);

        log.debug("put ResolvedTool, profileId={}, code={}, sourceId={}",
            resolvedTool.profileId(), resolvedTool.code(), resolvedTool.sourceId()
        );
    }

    /**
     * 按 {@code profileId} 热删除.
     */
    public void remove(Long profileId) {
        byCode.values().stream()
            .filter(existing -> existing.profileId().equals(profileId))
            .findAny()
            .ifPresent(existing -> {
                byCode.remove(existing.code());
                log.debug("remove ResolvedTool, profileId={}, code={}",
                    profileId, existing.code()
                );
            });
    }

    /**
     * 返回 {@code tools/list}：内存中的共享工具（NATIVE + EXTENSION）+ 当前租户的租户工具。
     * <p>
     * 原生工具不在这里做权限过滤。权限在 {@code tools/call} 时校验。
     * </p>
     */
    public List<McpSchema.Tool> listTools(McpRequestContext context, Set<String> allowlist) {
        Map<String, McpSchema.Tool> tools = new LinkedHashMap<>();
        for (ResolvedTool resolved : byCode.values()) {
            McpSchema.Tool tool = resolved.toolSpec().tool();
            if (allowlist.isEmpty() || allowlist.contains(tool.name())) {
                tools.put(tool.name(), tool);
            }
        }
        for (McpSchema.Tool tool : runtimeToolCatalog.list()) {
            if (tools.containsKey(tool.name())) {
                continue;
            }
            if (allowlist.isEmpty() || allowlist.contains(tool.name())) {
                tools.put(tool.name(), tool);
            }
        }
        log.debug("listTools sessionId={} size={}", context.sessionId(), tools.size());
        return List.copyOf(tools.values());
    }

    /**
     * 按 name 查找可执行规格。
     * <p>
     * 内存中的 NATIVE 无权限时抛出 {@link BusinessException}；EXTENSION 不鉴权。
     * 未命中内存时再查当前机构 CUSTOMER。
     * </p>
     */
    public Optional<McpServerFeatures.SyncToolSpecification> findToolSpec(McpRequestContext context, String toolName) {
        var resolvedTool = byCode.get(toolName);
        if (Objects.nonNull(resolvedTool)) {
            if (ResourceType.NATIVE.name().equals(resolvedTool.resourceType())) {
                nativeApiPermission.check(resolvedTool.sourceId());
                log.debug("findToolSpec native, sessionId={}, name={}", context.sessionId(), toolName);
            } else {
                log.debug("findToolSpec shared, sessionId={}, name={}, type={}",
                    context.sessionId(), toolName, resolvedTool.resourceType());
            }
            return Optional.of(resolvedTool.toolSpec());
        }
        return runtimeToolCatalog.find(toolName);
    }

    /**
     * profile 加载 / 热更入口: OpenAPI operation → 注册进内存缓存.
     *
     * @param profileId    档案主键
     * @param name         工具名
     * @param sourceId     NATIVE 的 resource_api.id，其余为空
     * @param resourceType 资源类型名
     * @param operation    运行时操作
     * @param inputSchema  档案中的 MCP inputSchema JSON
     */
    public void register(Long profileId, String name, @Nullable Long sourceId, String resourceType,
                         OpenApiOperation operation, String inputSchema) {
        var registeredTool = buildRegisteredTool(name, resourceType, operation, inputSchema);
        var toolSpec = toolSpecBuilder.buildSyncToolSpec(registeredTool);
        put(new ResolvedTool(profileId, name, sourceId, resourceType, toolSpec));
    }

    /**
     * 组装可执行规格，供租户工具在调用时现装。
     *
     * @param name         工具名
     * @param resourceType 资源类型名
     * @param operation    运行时操作
     * @param inputSchema  档案中的 MCP inputSchema JSON
     * @return 可执行规格
     */
    public McpServerFeatures.SyncToolSpecification specification(String name, String resourceType,
                                                                  OpenApiOperation operation, String inputSchema) {
        return toolSpecBuilder.buildSyncToolSpec(buildRegisteredTool(name, resourceType, operation, inputSchema));
    }

    /**
     * 组装注册工具：名称、inputSchema 取档案（导入时按文档版本生成），调用时按 Operation 分解参数。
     *
     * @param name         工具名
     * @param resourceType 资源类型名，随出站请求带给拦截器做计量、审计
     * @param operation    运行时操作
     * @param inputSchema  档案中的 MCP inputSchema JSON
     * @return 注册工具
     */
    public RegisteredTool buildRegisteredTool(String name, String resourceType, OpenApiOperation operation,
                                              String inputSchema) {
        var tool = McpSchema.Tool.builder(name, resolveJsonSchema(name, inputSchema))
            .title(resolveTitle(operation, name))
            .description(buildDescription(operation))
            .build();
        return new RegisteredTool(tool, (context, callToolReq) -> {
            var httpReqSpec = inputSchemaComposer.decompose(
                callToolReq, operation.operation()
            );
            return toolHandler.execute(context, httpReqSpec, operation, resourceType);
        });
    }

    private String resolveTitle(OpenApiOperation operation, String toolName) {
        var summary = operation.operation().getSummary();
        return Objects.nonNull(summary) ? summary : toolName;
    }

    private static final JsonCodec JSON = JsonCodecFactory.instance();

    private static final Map<String, Object> EMPTY_OBJECT_SCHEMA = Map.of("type", "object");

    private Map<String, Object> resolveJsonSchema(String name, @Nullable String inputSchema) {
        if (Strings.isBlank(inputSchema)) {
            return EMPTY_OBJECT_SCHEMA;
        }

        try {
            return JSON.str2map(inputSchema);
        } catch (RuntimeException exception) {
            log.error("Failed to resolve JSON schema for tool: {}", name, exception);
            throw new BusinessException(McpErrorCode.JSON_SCHEMA_RESOLVE_FAILED, name);
        }
    }

    private @Nullable String buildDescription(OpenApiOperation operation) {
        var summary = operation.operation().getSummary();
        var description = operation.operation().getDescription();
        String baseDescription = null;
        if (Strings.isNotBlank(description)) {
            baseDescription = description;
        } else if (Strings.isNotBlank(summary)) {
            baseDescription = summary;
        }

        return baseDescription;
    }
}
