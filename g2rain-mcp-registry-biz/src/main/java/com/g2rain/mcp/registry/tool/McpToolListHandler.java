package com.g2rain.mcp.registry.tool;


import com.g2rain.mcp.registry.config.McpToolHandlerRegister;
import com.g2rain.mcp.registry.context.McpRequestContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * MCP {@code tools/list} 请求处理器.
 * <p>
 * 当 LLM 客户端问「你有哪些 Tool 可以用」时, MCP 协议会发起 {@code tools/list} 请求.
 * 本类是该请求在 g2rain MCP Registry 内的<strong>唯一入口</strong>:
 * {@link McpToolHandlerRegister} 在 MCP Server 启动完成后,
 * 把默认 list 处理逻辑替换为本类的 {@link #handle(McpRequestContext, Set)}.
 * </p>
 *
 * <h2>调用链路</h2>
 * <pre>
 * McpToolHandlerPatcher
 *   -> McpRequestContexts.of(exchange)
 *   -> McpToolListHandler.handle
 *   -> ToolRegistry.listTools
 *   -> ListToolsResult
 * </pre>
 *
 * <h2>本类做什么</h2>
 * <ul>
 *   <li>从 {@link ToolRegistry} 取出当前请求<strong>有权看到</strong>的 Tool 元数据列表</li>
 *   <li>包装成 {@link McpSchema.ListToolsResult} 返回给 MCP 客户端</li>
 * </ul>
 *
 * <h2>本类不做什么</h2>
 * <ul>
 *   <li>不负责 Tool 缓存、profile 加载、inputSchema 构建 — 这些在 {@link ToolRegistry} 注册阶段已完成</li>
 *   <li>不负责权限规则的具体实现 — 委托 {@link ToolRegistry#listTools} 按 {@code sourceId} 过滤</li>
 * </ul>
 *
 * <h2>上下文说明</h2>
 * <p>
 * {@code tools/list} 没有 tool 调用参数, 因此 {@link McpRequestContext#callToolRequest()} 恒为 {@code null}.
 * {@link McpRequestContext#syncServerExchange()} 有值时可读取 {@code sessionId}, 便于日志关联.
 * list 与 {@link McpToolCallHandler} 共用同一个 {@link ToolRegistry},
 * 保证「list 里看到的 Tool」与「call 能执行的 Tool」一致.
 * </p>
 *
 * <h2>示例</h2>
 * <p>
 * 用户有权访问两个 API（sourceId 1001、1002）, list 返回:
 * </p>
 * <pre>{@code
 * [
 *   { "name": "get_order_by_id", "title": "按 ID 查询订单", "inputSchema": { ... } },
 *   { "name": "create_order",    "title": "创建订单",       "inputSchema": { ... } }
 * ]
 * }</pre>
 * <p>无权访问的 Tool 不会出现在列表中.</p>
 *
 * @author alpha
 * @see ToolRegistry#listTools(McpRequestContext, Set)
 * @see McpToolHandlerRegister
 * @see McpToolCallHandler
 * @since 2026/6/29
 */
@Component
@NullMarked
public class McpToolListHandler {

    /**
     * Tool 内存注册表: 按当前用户权限返回可见的 {@link McpSchema.Tool} 元数据.
     * <p>
     * list 与 call 共用此注册表, 权限过滤规则也保持一致.
     * </p>
     */
    private final ToolRegistry toolRegistry;

    /**
     * 构造 {@code tools/list} 处理器.
     *
     * @param toolRegistry tool 注册表, 由 Spring 容器注入
     */
    public McpToolListHandler(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 处理 MCP {@code tools/list} 请求.
     * <p>
     * 委托 {@link ToolRegistry#listTools} 获取 Tool 列表, 再封装为 {@link McpSchema.ListToolsResult}.
     * 无可见 Tool 时返回空列表, 不抛错.
     * </p>
     *
     * @param context 当前 MCP 请求上下文（含 HTTP 请求、会话 exchange）, 供注册表做权限过滤
     * @return 包含 Tool 元数据列表的结果; 永不为 {@code Mono.error}
     */
    public Mono<McpSchema.ListToolsResult> handle(McpRequestContext context, Set<String> allowedToolNames) {
        // 委托注册表: 按权限 sourceId 过滤 → SyncToolSpecification.tool()
        var tools = toolRegistry.listTools(context, allowedToolNames);

        // 使用 builder 构造结果, 避免使用已弃用的 ListToolsResult 两参构造器
        return Mono.just(McpSchema.ListToolsResult.builder(tools).build());
    }
}
