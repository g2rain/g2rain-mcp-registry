package com.g2rain.mcp.registry.support;


import com.g2rain.mcp.registry.context.McpRequestContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * MCP 工具调用进度通知的内容提供器.
 * <p>
 * 在 {@link com.g2rain.mcp.registry.tool.ToolHandler} 向下游 HTTP API 发起请求期间,
 * 框架会周期性向 MCP 客户端发送 {@code notifications/progress} 消息.
 * 本接口负责生成每次通知中的 {@code progress} 与 {@code message} 字段.
 * </p>
 * <p>
 * 调用时序:
 * </p>
 * <ol>
 *   <li>通知循环开始前, 框架调用 {@link #total(McpRequestContext)} 一次, 获取本次 tool 调用的预期总量</li>
 *   <li>之后每个 tick 间隔, 框架调用 {@link #next(long, McpRequestContext)} 生成下一次进度快照</li>
 *   <li>{@code total} 在单次 tool 调用生命周期内保持不变, 确保客户端收到的进度比例一致</li>
 * </ol>
 * <p>
 * 可通过 {@link McpRequestContext} 获取 tool 名称、OpenAPI 操作等信息,
 * 实现按操作定制进度文案, 而无需为每个 tool 单独注册 Bean.
 * </p>
 * <p>
 * 默认实现为 {@link com.g2rain.mcp.registry.support.progress.DefaultProgressUpdateProvider},
 * 可通过声明自定义 Bean 替换.
 * </p>
 * <p><strong>示例 — 按 tool 名称生成静态提示文案:</strong></p>
 * <pre>{@code
 * @Bean
 * public ProgressUpdateProvider progressUpdateProvider() {
 *     return new ProgressUpdateProvider() {
 *         @Override
 *         public Double total(McpRequestContext context) {
 *             return null; // 未知总时长, 通知中省略 total 字段
 *         }
 *
 *         @Override
 *         public ProgressUpdate next(long tick, McpRequestContext context) {
 *             var toolName = context.toolName() != null ? context.toolName() : "tool";
 *             return new ProgressUpdate(tick, "正在等待 " + toolName + " 完成…");
 *         }
 *     };
 * }
 * }</pre>
 *
 * @author alpha
 * @since 2026/6/30
 * @see ProgressUpdate
 * @see com.g2rain.mcp.registry.context.McpRequestContext#progressNotification()
 * @see com.g2rain.mcp.registry.support.progress.DefaultProgressUpdateProvider
 */
@NullMarked
public interface ProgressUpdateProvider {

    /**
     * 返回本次 tool 调用完成时的预期进度总量, 或 {@code null} 表示总时长未知.
     * <p>
     * 在进度通知循环启动前调用一次. 返回值会在本次 tool 调用的所有
     * {@code notifications/progress} 消息中原样携带, 供客户端计算完成百分比.
     * </p>
     * <p>
     * 返回 {@code null} 时, 通知中不包含 {@code total} 字段,
     * 客户端通常仅展示递增的 {@code progress} 计数或文案.
     * </p>
     *
     * @param context 当前 MCP 请求上下文, 含 tool 名称、会话 exchange 等
     * @return 预期总量; 未知时返回 {@code null}
     */
    @Nullable
    Double total(McpRequestContext context);

    /**
     * 为给定 tick 生成下一次进度更新内容.
     * <p>
     * 返回的 {@link ProgressUpdate#progress()} 必须严格大于上一次 tick 返回的值,
     * 以满足 MCP 协议对进度单调递增的要求.
     * </p>
     * <p>
     * {@code tick} 由框架维护, 从 0 起每个通知间隔自增 1,
     * 与 wall-clock 无直接绑定, 便于测试时替换 {@code NotificationTicker}.
     * </p>
     *
     * @param tick    从 0 开始的零基计数, 每个通知间隔由框架递增
     * @param context 当前 MCP 请求上下文
     * @return 待发送的进度快照, 不可为 null
     */
    ProgressUpdate next(long tick, McpRequestContext context);
}
