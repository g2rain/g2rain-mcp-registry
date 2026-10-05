package com.g2rain.mcp.registry.context;


import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 单次 MCP 请求的统一上下文.
 * <p>
 * g2rain MCP Registry 在 {@code tools/list} 与 {@code tools/call} 链路中,
 * 需要同时访问「Web 层 HTTP 请求」「MCP 会话信息」「tool 调用参数」.
 * 本 record 把三者聚合为一个不可变对象, 供 Handler、Filter、Enricher、进度通知等组件读取.
 * </p>
 *
 * <h2>三个组成部分</h2>
 * <ul>
 *   <li>{@link #httpServletRequest()} — 当前 HTTP 请求, 可读 Header、URI 等; 非 Web 线程时为 null</li>
 *   <li>{@link #syncServerExchange()} — MCP 会话对象, 可读 sessionId、clientInfo、发进度通知; 无会话时为 null</li>
 *   <li>{@link #callToolRequest()} — {@code tools/call} 请求体; {@code tools/list} 时为 null</li>
 * </ul>
 *
 * <h2>典型场景</h2>
 * <ul>
 *   <li><strong>tools/list</strong>: exchange 有值, callToolRequest 为 null</li>
 *   <li><strong>tools/call</strong>: exchange 与 callToolRequest 均有值</li>
 * </ul>
 *
 * @param httpServletRequest 当前 HTTP 请求
 * @param syncServerExchange MCP 会话交换对象
 * @param callToolRequest    MCP tool 调用请求; list 场景为 null
 * @author alpha
 * @since 2026/6/29
 * @see McpRequestContexts
 */
@NullMarked
public record McpRequestContext(@Nullable HttpServletRequest httpServletRequest, @Nullable McpSyncServerExchange syncServerExchange, McpSchema.@Nullable CallToolRequest callToolRequest) {

    /**
     * 返回当前 MCP 会话 ID.
     * <p>
     * 仅在存在 {@link #syncServerExchange()} 时有效, 可用于日志关联与多租户隔离.
     * </p>
     *
     * @return 会话 ID; 无 exchange 时返回 null
     */
    public @Nullable String sessionId() {
        // STREAMABLE 模式下从 exchange 读取 sessionId; STATELESS 或无 exchange 时返回 null
        return Objects.nonNull(syncServerExchange) ? syncServerExchange.sessionId() : null;
    }

    /**
     * 返回 MCP 客户端实现信息.
     * <p>
     * 包含客户端 name, version 等, 可用于审计或按客户端差异化处理.
     * </p>
     *
     * @return 客户端信息; 无 exchange 时返回 null
     */
    public McpSchema.@Nullable Implementation clientInfo() {
        // 从 exchange 委托读取 initialize 阶段上报的 clientInfo
        return Objects.nonNull(syncServerExchange) ? syncServerExchange.getClientInfo() : null;
    }

    /**
     * 返回本次调用的 tool 名称.
     * <p>
     * 仅在 {@code tools/call} 场景下有意义; {@code tools/list} 时 {@link #callToolRequest()} 为 null,
     * 本方法同样返回 null.
     * </p>
     *
     * @return tool 名称; 无 call 请求时返回 null
     */
    public @Nullable String toolName() {
        // 从 CallToolRequest 中提取 name 字段, 避免下游重复判空访问
        return Objects.nonNull(callToolRequest) ? callToolRequest.name() : null;
    }

    /**
     * 返回向 MCP 客户端推送进度通知的回调.
     * <p>
     * 长时间运行的 tool 可通过该回调发送 {@link McpSchema.ProgressNotification};
     * STATELESS 或无 exchange 时不可用.
     * </p>
     *
     * @return 进度通知发送函数; 无 exchange 时返回 null
     */
    public @Nullable Consumer<McpSchema.ProgressNotification> progressNotification() {
        // 将 exchange 的 progressNotification 方法引用暴露给 ProgressUpdateProvider 等组件
        return Objects.nonNull(syncServerExchange) ? syncServerExchange::progressNotification : null;
    }
}
