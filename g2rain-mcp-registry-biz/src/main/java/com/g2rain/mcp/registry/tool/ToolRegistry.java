package com.g2rain.mcp.registry.tool;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.context.McpRequestContext;
import com.g2rain.mcp.registry.enums.McpErrorCode;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.openapi.naming.NamingStrategy;
import com.g2rain.mcp.registry.perm.BuiltInApiPermission;
import com.g2rain.mcp.registry.runtime.RuntimeToolCatalog;
import com.g2rain.mcp.registry.schema.InputSchemaComposer;
import com.g2rain.mcp.registry.tool.filter.RegisteredTool;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.core.util.Json;
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
    private final NamingStrategy namingStrategy;
    private final ToolHandler toolHandler;
    private final ToolSpecBuilder toolSpecBuilder;
    private final RuntimeToolCatalog runtimeToolCatalog;
    private final BuiltInApiPermission builtInApiPermission;

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
     * 返回 {@code tools/list}：内存中的共享工具（BUILT_IN + EXTENSION）+ 当前租户的客户工具。
     * <p>
     * 内置工具不在这里做权限过滤。权限在 {@code tools/call} 时校验。
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
     * 内存中的 BUILT_IN 无权限时抛出 {@link BusinessException}；EXTENSION 不鉴权。
     * 未命中内存时再查当前机构 CUSTOMER。
     * </p>
     */
    public Optional<McpServerFeatures.SyncToolSpecification> findToolSpec(McpRequestContext context, String toolName) {
        var resolvedTool = byCode.get(toolName);
        if (Objects.nonNull(resolvedTool)) {
            if (ResourceType.BUILT_IN.name().equals(resolvedTool.resourceType())) {
                builtInApiPermission.check(resolvedTool.sourceId());
                log.debug("findToolSpec built-in, sessionId={}, name={}", context.sessionId(), toolName);
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
     */
    public void register(Long profileId, String code, @Nullable Long sourceId, String resourceType,
                         OpenApiOperation operation) {
        var registeredTool = buildRegisteredTool(code, operation);
        var toolSpec = toolSpecBuilder.buildSyncToolSpec(registeredTool);
        put(new ResolvedTool(profileId, code, sourceId, resourceType, toolSpec));
    }

    /**
     * 按指定工具名组装可执行规格，供客户工具在调用时现装。
     */
    public McpServerFeatures.SyncToolSpecification specification(String toolName, OpenApiOperation operation) {
        return toolSpecBuilder.buildSyncToolSpec(buildRegisteredTool(toolName, operation));
    }

    /**
     * {@code toolName} 有值时用档案里的名称；空白则按命名策略现算。
     */
    public RegisteredTool buildRegisteredTool(@Nullable String toolName, OpenApiOperation operation) {
        var resolvedName = Strings.isNotBlank(toolName) ? toolName : determineToolName(operation);
        var tool = McpSchema.Tool.builder(resolvedName, resolveJsonSchema(operation))
            .title(resolveTitle(operation, resolvedName))
            .description(buildDescription(operation))
            .build();

        return new RegisteredTool(tool, (context, callToolReq) -> {
            var httpReqSpec = inputSchemaComposer.decompose(
                callToolReq, operation.operation()
            );

            return toolHandler.execute(context, httpReqSpec, operation);
        });
    }

    private String determineToolName(OpenApiOperation operation) {
        try {
            return namingStrategy.name(operation);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(
                McpErrorCode.NAME_CANNOT_BE_DETERMINED,
                operation.method(),
                operation.path(),
                exception.getMessage()
            );
        }
    }

    private String resolveTitle(OpenApiOperation operation, String toolName) {
        var summary = operation.operation().getSummary();
        return Objects.nonNull(summary) ? summary : toolName;
    }

    private static final Map<String, Object> EMPTY_OBJECT_SCHEMA = Map.of("type", "object");

    private static final TypeReference<Map<String, Object>> INPUT_SCHEMA_TYPE = new TypeReference<>() {

    };

    private Map<String, Object> resolveJsonSchema(OpenApiOperation operation) {
        var composedSchema = inputSchemaComposer.compose(operation);
        if (Objects.isNull(composedSchema)) {
            return EMPTY_OBJECT_SCHEMA;
        }

        try {
            var mapper = Json.mapper();
            var stringSchemaRepresentation = mapper.writeValueAsString(composedSchema);
            log.debug(
                "Resolved JSON schema for operation {}: {}",
                operation.operation().getOperationId(),
                stringSchemaRepresentation
            );
            return mapper.readValue(stringSchemaRepresentation, INPUT_SCHEMA_TYPE);
        } catch (JsonProcessingException exception) {
            log.error(
                "Failed to resolve JSON schema for operation: {}",
                operation.operation().getOperationId(),
                exception
            );
            throw new BusinessException(
                McpErrorCode.JSON_SCHEMA_RESOLVE_FAILED,
                null,
                new Object[]{operation.operation().getOperationId()},
                null,
                exception
            );
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
