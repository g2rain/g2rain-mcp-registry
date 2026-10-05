package com.g2rain.mcp.registry.support;


import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * 单条 {@code notifications/progress} 消息的业务载荷.
 * <p>
 * 由 {@link ProgressUpdateProvider#next(long, com.g2rain.mcp.registry.context.McpRequestContext)}
 * 在每次 tick 产生, 经 {@link com.g2rain.mcp.registry.context.McpRequestContext#progressNotification()}
 * 推送给 MCP 客户端.
 * </p>
 * <p>
 * {@code progress} 必须在同一次 tool 调用内单调递增;
 * {@code message} 为可选的人类可读状态文案, 支持客户端展示给用户.
 * </p>
 * <p>
 * {@code total} 不在本 record 中: 它由 {@link ProgressUpdateProvider#total(com.g2rain.mcp.registry.context.McpRequestContext)}
 * 在循环开始前解析一次, 并在整个 tool 调用期间保持不变.
 * 这样可避免 {@code total} 在多次通知间漂移, 导致客户端进度条比例跳动.
 * </p>
 *
 * @param progress 当前进度值; 必须大于同一次 tool 调用中上一次通知的 {@code progress}
 * @param message  可选状态说明; {@code null} 表示通知中省略 message 字段
 * @author alpha
 * @since 2026/6/30
 * @see ProgressUpdateProvider
 */
@NullMarked
public record ProgressUpdate(double progress, @Nullable String message) {
}
