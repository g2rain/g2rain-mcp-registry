package com.g2rain.mcp.registry.tool.filter;


import com.g2rain.mcp.registry.context.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.Ordered;

/**
 * MCP {@code tools/call} 责任链中的单个节点.
 * <p>
 * 每个 Filter 可以在调用前后做横切处理（记指标、鉴权扩展等）,
 * 然后决定是继续传给下一个节点 ({@code chain.doFilter}), 还是直接返回结果.
 * </p>
 *
 * <h2>排序规则</h2>
 * <p>
 * 继承 {@link Ordered}, 通过 {@link #getOrder()} 控制顺序: 值越小越先执行.
 * 末级执行节点 {@link RegisteredTool} 固定使用 {@link #LOWEST_PRECEDENCE}, 保证最后才发 HTTP.
 * </p>
 *
 * <h2>典型写法</h2>
 * <pre>{@code
 * public CallToolResult doFilter(McpRequestContext ctx, CallToolRequest req, ToolCallFilterChain chain) {
 *     // 前置逻辑 ...
 *     var result = chain.doFilter(ctx, req);  // 交给下一个 Filter
 *     // 后置逻辑 ...
 *     return result;
 * }
 * }</pre>
 *
 * @see ToolCallFilterChain
 * @see ToolCallFilterChains
 * @see RegisteredTool
 * @author alpha
 * @since 2026/6/29
 */
@NullMarked
public interface ToolCallFilter extends Ordered {

    /**
     * 处理一次 tool call.
     * <p>
     * 横切 Filter 通常先执行前置逻辑, 再调用 {@code chain.doFilter(ctx, req)} 驱动下一个节点;
     * 末级 {@link RegisteredTool} 直接执行 HTTP, 不再调用 chain.
     * </p>
     *
     * @param ctx   MCP 请求上下文
     * @param req   tool call 请求（含 name、arguments）
     * @param chain 责任链, 用于调用下一个 Filter
     * @return tool call 结果
     */
    McpSchema.CallToolResult doFilter(McpRequestContext ctx, McpSchema.CallToolRequest req, ToolCallFilterChain chain);
}
