package com.g2rain.mcp.registry.tool;


import io.modelcontextprotocol.server.McpServerFeatures;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * 一条已解析、可执行的 MCP Tool, 缓存在 {@link ToolRegistry} 内存注册表中.
 * <p>
 * 对应数据库 {@code mcp_tool_profile} 的一条 ACTIVE 记录 + 其关联 OpenAPI 操作.
 * list 时读取 {@link #toolSpec()}{@code .tool()}, call 时执行 {@link #toolSpec()}{@code .callHandler()}.
 * </p>
 *
 * @param profileId    profile 主键
 * @param code         MCP tool 名称
 * @param sourceId     权限过滤用的来源标识（NATIVE 为 resource_api.id；EXTENSION 可空）
 * @param resourceType {@code NATIVE} / {@code EXTENSION}；决定 call 是否走原生接口鉴权
 * @param toolSpec     可 list、可 call 的完整 Tool 规格
 * @author alpha
 * @see ToolRegistry
 * @see ToolSpecBuilder
 * @since 2026/7/1
 */
@NullMarked
public record ResolvedTool(Long profileId, String code, @Nullable Long sourceId, String resourceType,
                           McpServerFeatures.SyncToolSpecification toolSpec) {

}
