package com.g2rain.mcp.registry.tool.filter;


import com.g2rain.mcp.registry.context.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NonNull;

/**
 * MCP tool call 责任链, 由 {@link ToolCallFilterChains} 在注册阶段创建.
 * <p>
 * Filter 通过 {@code chain.doFilter(ctx, req)} 把控制权交给下一个节点.
 * 链内维护 {@code index} 游标, 每次 {@code tools/call} 必须 {@code get()} 新实例, 不可跨请求复用.
 * </p>
 *
 * <h2>示例</h2>
 * <p>链中有 FilterA(order=100) 和 RegisteredTool(order=LOWEST):</p>
 * <pre>
 * FilterA.doFilter:
 *   前置逻辑
 *   chain.doFilter  ->  RegisteredTool.doFilter -> ToolHandler.execute
 *   后置逻辑
 *   return result
 * </pre>
 *
 * @author alpha
 * @since 2026/6/29
 * @see ToolCallFilter
 * @see ToolCallFilterChains
 */
public interface ToolCallFilterChain {

    /**
     * 驱动责任链执行下一个 Filter.
     * <p>
     * 由当前 Filter 在需要继续向下传递时调用; 链耗尽且无 Filter 返回结果时抛异常.
     * </p>
     *
     * @param ctx MCP 请求上下文
     * @param req tool call 请求
     * @return 某个 Filter 或末级 RegisteredTool 产出的结果
     */
    McpSchema.@NonNull CallToolResult doFilter(@NonNull McpRequestContext ctx, McpSchema.@NonNull CallToolRequest req);
}
