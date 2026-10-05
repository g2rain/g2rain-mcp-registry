package com.g2rain.mcp.registry.tool.filter;


import com.g2rain.mcp.registry.context.McpRequestContext;
import com.g2rain.mcp.registry.tool.ToolRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NullMarked;

import java.util.function.BiFunction;

/**
 * 已注册的 MCP Tool: 既是要返回给 list 的元数据, 也是 call 链的<strong>末级执行节点</strong>.
 * <p>
 * 由 {@link ToolRegistry#buildRegisteredTool} 在注册阶段创建,
 * 再经 {@link com.g2rain.mcp.registry.tool.ToolSpecBuilder} 与横切 Filter 合并成完整 call 链.
 * </p>
 *
 * <h2>两个角色</h2>
 * <ul>
 *   <li>{@link #tool()} — MCP 元数据（name、title、inputSchema）, {@code tools/list} 直接返回</li>
 *   <li>{@link #toolHandlerFunc()} — 真正执行逻辑: 分解 arguments → {@link com.g2rain.mcp.registry.tool.ToolHandler#execute}</li>
 * </ul>
 *
 * <h2>在 Filter 链中的位置</h2>
 * <p>
 * {@code getOrder() = LOWEST_PRECEDENCE}, 保证所有横切 Filter 执行完后才调用 {@code toolHandlerFunc}.
 * 本类不再调用 {@code chain.doFilter}, 直接返回 HTTP 结果.
 * </p>
 *
 * <h2>示例</h2>
 * <p>
 * Tool {@code create_order} 的 RegisteredTool:
 * </p>
 * <ul>
 *   <li>{@code tool.name = "create_order"}</li>
 *   <li>{@code toolHandlerFunc} 收到 arguments {@code {"_body":{"customerId":"c1","items":[...]}}},
 *       分解后 POST 到 {@code lb://g2rain-basis/orders}</li>
 * </ul>
 *
 * @param tool            MCP tool 元数据
 * @param toolHandlerFunc 末级执行函数, 入参为 context + CallToolRequest, 出参为 CallToolResult
 * @author alpha
 * @see ToolCallFilterChains
 * @see ToolCallFilterChain
 * @see com.g2rain.mcp.registry.tool.ToolSpecBuilder
 * @since 2026/7/1
 */
@NullMarked
public record RegisteredTool(McpSchema.Tool tool, BiFunction<McpRequestContext, McpSchema.CallToolRequest, McpSchema.CallToolResult> toolHandlerFunc) implements ToolCallFilter {

    /**
     * 作为 filter 链末级节点执行 tool 调用.
     * <p>
     * 不继续驱动 {@code chain}, 直接调用 {@link #toolHandlerFunc} 完成本次 tool 执行.
     * </p>
     *
     * @param req   MCP {@code tools/call} 请求
     * @param ctx   当前 MCP 请求上下文
     * @param chain 责任链 (本实现不使用, 末级 filter 直接返回结果)
     * @return tool 调用结果
     */
    @Override
    public McpSchema.CallToolResult doFilter(McpRequestContext ctx, McpSchema.CallToolRequest req, ToolCallFilterChain chain) {
        return toolHandlerFunc.apply(ctx, req);
    }

    /**
     * 返回 filter 排序值, 固定为 {@link ToolCallFilter#LOWEST_PRECEDENCE}, 保证在横切 filter 之后执行.
     *
     * @return {@link ToolCallFilter#LOWEST_PRECEDENCE}
     */
    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
