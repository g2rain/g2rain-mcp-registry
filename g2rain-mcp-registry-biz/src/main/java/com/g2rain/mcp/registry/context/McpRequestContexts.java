package com.g2rain.mcp.registry.context;


import com.g2rain.mcp.registry.config.McpToolHandlerRegister;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link McpRequestContext} 的工厂类.
 * <p>
 * {@link McpToolHandlerRegister} 在 MCP list/call 回调里收到
 * exchange 或 transport 参数后, 通过本类组装统一的 {@link McpRequestContext}.
 * 同时从 Spring {@link RequestContextHolder} 提取当前 {@link HttpServletRequest},
 * 供 Principal 透传等 enricher 使用.
 * </p>
 *
 * <h2>STREAMABLE 模式（当前默认）</h2>
 * <ul>
 *   <li>{@link #of(McpSyncServerExchange)} — list 场景, 无 CallToolRequest</li>
 *   <li>{@link #of(McpSyncServerExchange, CallToolRequest)} — call 场景</li>
 * </ul>
 *
 * <h2>STATELESS 模式（预留）</h2>
 * <p>
 * 无持久 session, exchange 恒为 null; 仅保留 HTTP 与 CallToolRequest.
 * </p>
 *
 * @author alpha
 * @since 2026/6/29
 * @see McpRequestContext
 * @see McpToolHandlerRegister
 */
@NullMarked
public class McpRequestContexts {

    /**
     * 私有构造, 禁止实例化.
     */
    private McpRequestContexts() {

    }

    /**
     * 为 STREAMABLE/SSE 等会话模式构造上下文 (不含 tool 调用参数).
     * <p>
     * 适用于 {@code tools/list} 等不需要 {@link McpSchema.CallToolRequest} 的请求.
     * </p>
     *
     * @param ex MCP 同步服务端 exchange, 由 {@link McpToolHandlerRegister} 从 SDK 回调参数包装得到
     * @return 包含 HTTP 请求与 exchange 的上下文, {@code callToolRequest} 为 null
     */
    public static McpRequestContext of(McpSyncServerExchange ex) {
        // 委托到双参数重载, call 请求体置 null
        return of(ex, null);
    }

    /**
     * 为 STREAMABLE/SSE 等会话模式构造完整上下文.
     * <p>
     * 适用于 {@code tools/call}, 同时携带 exchange 与解析后的 {@link McpSchema.CallToolRequest}.
     * </p>
     *
     * @param ex  MCP 同步服务端 exchange
     * @param req MCP tool 调用请求; list 场景传 null
     * @return 组装完成的 {@link McpRequestContext}
     */
    public static McpRequestContext of(McpSyncServerExchange ex, McpSchema.@Nullable CallToolRequest req) {
        // HTTP 从当前 Spring Web 请求上下文获取; exchange 与 call 请求由调用方传入
        return new McpRequestContext(currentRequest(), ex, req);
    }

    /**
     * 为 STATELESS 模式构造上下文 (不含 tool 调用参数).
     * <p>
     * 无持久 session, 故 {@link McpRequestContext#syncServerExchange()} 恒为 null.
     * 当前项目未启用 STATELESS 协议, 此方法仅在协议切换后由 {@link McpToolHandlerRegister} 调用.
     * </p>
     *
     * @param c MCP 传输上下文, 当前实现未读取其内容, 保留以匹配 SDK handler 签名
     * @return 仅含 HTTP 与 null exchange 的上下文
     */
    public static McpRequestContext of(@Nullable McpTransportContext c) {
        // 委托到双参数重载, call 请求体置 null
        return of(c, null);
    }

    /**
     * 为 STATELESS 模式构造完整上下文.
     * <p>
     * 与 STREAMABLE 不同, 不提供 sessionId, clientInfo, progress 等会话能力.
     * </p>
     *
     * @param ignored 传输上下文, 当前未使用; 若将来自定义 {@code contextExtractor} 可从此处读取元数据
     * @param req     MCP tool 调用请求; list 场景传 null
     * @return 组装完成的 {@link McpRequestContext}, exchange 字段为 null
     */
    public static McpRequestContext of(@Nullable McpTransportContext ignored, McpSchema.@Nullable CallToolRequest req) {
        // STATELESS 无 McpSyncServerExchange; HTTP 仍通过 RequestContextHolder 获取
        return new McpRequestContext(currentRequest(), null, req);
    }

    /**
     * 从 Spring Web 请求上下文中获取当前 {@link HttpServletRequest}.
     * <p>
     * WebMvc 处理 MCP 请求时, 通常在同一线程内已绑定 {@link RequestContextHolder},
     * 因此 STREAMABLE 模式下多数情况能取到有效请求对象.
     * </p>
     *
     * @return 当前 HTTP 请求; 非 Web 环境或未绑定时返回 null
     */
    private static @Nullable HttpServletRequest currentRequest() {
        try {
            // 读取当前线程绑定的 Spring 请求属性
            var requestAttributes = RequestContextHolder.currentRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes attributes) {
                // 从 Servlet 属性中提取 HttpServletRequest
                return attributes.getRequest();
            }
        } catch (IllegalStateException _) {
            // 无 Web 请求上下文 (如 stdio 或非请求线程), 静默返回 null
        }

        return null;
    }
}
