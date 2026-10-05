package com.g2rain.mcp.registry.tool;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.context.McpRequestContext;
import com.g2rain.mcp.registry.enums.McpErrorCode;
import com.g2rain.mcp.registry.openapi.naming.NamingStrategy;
import com.g2rain.mcp.registry.schema.InputSchemaComposer;
import com.g2rain.mcp.registry.tool.filter.RegisteredTool;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.core.util.Json;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Tool 注册表, {@code tools/list} 与 {@code tools/call} 共用.
 * <p>
 * 内存缓存 {@link ResolvedTool}, 双索引:
 * {@code byCode} 供 call 定位; {@code bySourceId → code} 供 list 按权限 sourceId 集合过滤.
 * </p>
 *
 * @author alpha
 * @since 2026/6/29
 */
@Slf4j
@Component
@NullMarked
public class ToolRegistry {

    private final ConcurrentHashMap<String, ResolvedTool> byCode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> bySourceId = new ConcurrentHashMap<>();

    private final InputSchemaComposer inputSchemaComposer;
    private final NamingStrategy namingStrategy;
    private final ToolHandler toolHandler;
    private final ToolSpecBuilder toolSpecBuilder;

    public ToolRegistry(
        InputSchemaComposer inputSchemaComposer,
        NamingStrategy namingStrategy,
        ToolHandler toolHandler,
        ToolSpecBuilder toolSpecBuilder
    ) {
        this.inputSchemaComposer = inputSchemaComposer;
        this.namingStrategy = namingStrategy;
        this.toolHandler = toolHandler;
        this.toolSpecBuilder = toolSpecBuilder;
    }


    /**
     * 写入或热更新一条 {@link ResolvedTool}.
     */
    public void put(ResolvedTool resolvedTool) {
        byCode.values().stream()
            .filter(existing -> existing.profileId().equals(resolvedTool.profileId()))
            .findAny()
            .ifPresent(existing -> {
                byCode.remove(existing.code());
                bySourceId.remove(existing.sourceId(), existing.code());
            });

        byCode.put(resolvedTool.code(), resolvedTool);
        bySourceId.put(resolvedTool.sourceId(), resolvedTool.code());

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
                bySourceId.remove(existing.sourceId(), existing.code());
                log.debug("remove ResolvedTool, profileId={}, code={}",
                    profileId, existing.code()
                );
            });
    }

    /**
     * 返回当前请求可见的 {@code tools/list} 元数据.
     */
    public List<McpSchema.Tool> listTools(McpRequestContext context, Set<String> allowlist) {
        return resolvePermittedSourceIds(context).stream()
            .map(bySourceId::get)
            .filter(Objects::nonNull)
            .map(byCode::get)
            .filter(Objects::nonNull)
            .map(ResolvedTool::toolSpec)
            .map(McpServerFeatures.SyncToolSpecification::tool)
            .filter(t -> allowlist.isEmpty() || allowlist.contains(t.name()))
            .toList();
    }

    /**
     * 按 name 查找 {@link McpServerFeatures.SyncToolSpecification}, 同 SDK {@code this.tools.stream()} 语义.
     * <p>
     * 不存在或无权限时返回 {@link Optional#empty()}.
     * </p>
     */
    public Optional<McpServerFeatures.SyncToolSpecification> findToolSpec(McpRequestContext context, String toolName) {
        var resolvedTool = byCode.get(toolName);
        if (Objects.isNull(resolvedTool)) {
            return Optional.empty();
        }

        if (!resolvePermittedSourceIds(context).contains(resolvedTool.sourceId())) {
            return Optional.empty();
        }

        return Optional.of(resolvedTool.toolSpec());
    }

    private Collection<Long> resolvePermittedSourceIds(McpRequestContext context) {
        // TODO: 从 context 解析登录身份, 查询权限范围内的 sourceId (resource_api.id)
        log.debug("resolvePermittedSourceIds, sessionId={}", context.sessionId());
        return bySourceId.keySet();
    }

    /**
     * profile 加载 / 热更入口: OpenAPI operation → 注册进内存缓存.
     * <p>
     * {@code buildRegisteredTool → buildSyncToolSpec → ResolvedTool → put}.
     * </p>
     */
    public void register(Long profileId, String code, Long sourceId, OpenApiOperation operation) {
        var registeredTool = buildRegisteredTool(operation);
        var toolSpec = toolSpecBuilder.buildSyncToolSpec(registeredTool);
        put(new ResolvedTool(profileId, code, sourceId, toolSpec));
    }

    /**
     * 从 OpenAPI operation 构建 {@link RegisteredTool} (仅元数据 + 末级 handler, 不含 filter 链).
     */
    public RegisteredTool buildRegisteredTool(OpenApiOperation operation) {
        var toolName = determineToolName(operation);
        var tool = McpSchema.Tool.builder(toolName, resolveJsonSchema(operation))
            .title(resolveTitle(operation, toolName))
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
