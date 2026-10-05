package com.g2rain.mcp.registry.tool;


import com.g2rain.mcp.registry.tool.filter.RegisteredTool;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NullMarked;

/**
 * 一条已解析、可执行的 MCP Tool, 缓存在 {@link ToolRegistry} 内存注册表中.
 * <p>
 * 对应数据库 {@code mcp_tool_profile} 的一条 ACTIVE 记录 + 其关联 OpenAPI 操作.
 * list 时读取 {@link #toolSpec()}{@code .tool()}, call 时执行 {@link #toolSpec()}{@code .callHandler()}.
 * </p>
 *
 * <h2>构建链路</h2>
 * <pre>
 * McpToolProfilePo + OpenApiOperation
 *   -> ToolRegistry.buildRegisteredTool
 *   -> ToolSpecBuilder.buildSyncToolSpec
 *   -> ResolvedTool
 *   -> ToolRegistry.put
 * </pre>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>{@code profileId} — {@code mcp_tool_profile.id}, 热更新/删除时的主键</li>
 *   <li>{@code code} — MCP tool name, 即 {@code tools/call} 请求里的 {@code name}</li>
 *   <li>{@code sourceId} — 来源标识, 当前语义为 {@code basis.resource_api.id}, 用于权限过滤</li>
 *   <li>{@code toolSpec} — 含 Tool 元数据与 callHandler 的完整规格</li>
 * </ul>
 *
 * <h2>示例</h2>
 * <pre>
 * profileId = 90001
 * code      = get_order_by_id
 * sourceId  = 1001
 * toolSpec  = SyncToolSpecification(name=get_order_by_id, inputSchema=..., callHandler=...)
 * </pre>
 *
 * @param profileId profile 主键
 * @param code      MCP tool 名称
 * @param sourceId  权限过滤用的来源标识
 * @param toolSpec  可 list、可 call 的完整 Tool 规格
 * @author alpha
 * @since 2026/7/1
 * @see ToolRegistry
 * @see ToolSpecBuilder
 */
@NullMarked
public record ResolvedTool(Long profileId, String code, Long sourceId, McpServerFeatures.SyncToolSpecification toolSpec) {

}
