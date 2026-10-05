package com.g2rain.mcp.registry.support.progress;

import com.g2rain.mcp.registry.context.McpRequestContext;
import com.g2rain.mcp.registry.support.ProgressUpdate;
import com.g2rain.mcp.registry.support.ProgressUpdateProvider;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link ProgressUpdateProvider} 的默认实现.
 * <p>
 * 向 MCP 客户端发送单调递增的 tick 计数作为 {@code progress}, 不携带 {@code total} 与 {@code message}.
 * 适用于「仅告知客户端请求仍在进行、总时长未知」的场景.
 * </p>
 * <p>
 * 行为说明:
 * </p>
 * <ul>
 *   <li>{@link #total(McpRequestContext)} 恒返回 {@code null}, 进度通知中省略 total 字段</li>
 *   <li>{@link #next(long, McpRequestContext)} 将 {@code progress} 设为当前 {@code tick},
 *       满足 MCP 要求: 每次通知的 progress 严格大于上一次</li>
 *   <li>忽略 {@code context} 中的 tool/operation 信息, 不做差异化文案</li>
 * </ul>
 * <p>
 * 可通过 Spring {@code @ConditionalOnMissingBean(ProgressUpdateProvider.class)} 注册为默认 Bean;
 * 应用声明自定义 {@link ProgressUpdateProvider} 即可覆盖.
 * </p>
 *
 * @author alpha
 * @since 2026/6/30
 * @see ProgressUpdateProvider
 * @see ProgressUpdate
 */
@NullMarked
public class DefaultProgressUpdateProvider implements ProgressUpdateProvider {

    /**
     * 返回本次 tool 调用的预期进度总量。
     * <p>
     * 默认实现始终返回 {@code null}，表示无法预估完成总量；
     * 进度通知中将省略 total 字段，客户端仅看到递增的 progress。
     * </p>
     *
     * @param context 当前 MCP 请求上下文
     * @return 恒为 {@code null}
     */
    @Override
    public @Nullable Double total(McpRequestContext context) {
        // 未知总进度: 客户端仅看到递增的 progress, 无法计算百分比
        return null;
    }

    /**
     * 为给定 tick 生成下一次进度更新内容。
     * <p>
     * 默认实现将 tick 原样作为 progress，message 为 null；
     * tick=0,1,2,… 天然单调递增，无需额外状态，满足 MCP 进度严格递增要求。
     * </p>
     *
     * @param tick    从 0 开始的零基计数，每个通知间隔由框架递增
     * @param context 当前 MCP 请求上下文（本实现忽略其中的 tool/operation 文案）
     * @return 待发送的进度快照，{@code progress=tick}，message 为空
     */
    @Override
    public ProgressUpdate next(long tick, McpRequestContext context) {
        // progress 取 tick, 保证每次通知严格大于上一次; message 省略
        return new ProgressUpdate(tick, null);
    }
}
