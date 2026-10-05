package com.g2rain.mcp.registry.tool.filter;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.mcp.registry.context.McpRequestContext;
import com.g2rain.mcp.registry.enums.McpErrorCode;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * MCP tool call 责任链的组装工厂.
 * <p>
 * 注册阶段把 {@link RegisteredTool} 与所有横切 {@link ToolCallFilter} 按 {@link Ordered} 排序,
 * 封装为 {@link Supplier}{@code <ToolCallFilterChain>}; 每次 call 时 {@code get()} 得到新链实例.
 * </p>
 *
 * <h2>为什么要 Supplier</h2>
 * <p>
 * 链内用 {@code index} 记录「执行到第几个 Filter」. 若复用同一链对象, 第二次 call 会从中间继续, 导致错乱.
 * 因此注册时只 sort 一次, 每次 call 再 {@code get()} 新链, {@code index} 从 0 开始.
 * </p>
 *
 * <h2>使用方式</h2>
 * <pre>
 * // 注册阶段（ToolSpecBuilder）
 * Supplier&lt;ToolCallFilterChain&gt; supplier = ToolCallFilterChains.of(registeredTool, filters);
 *
 * // 每次 tools/call
 * supplier.get().doFilter(context, request);
 * </pre>
 *
 * <h2>示例</h2>
 * <p>
 * filters = [MetricFilter(order=50)], registeredTool(order=LOWEST):
 * </p>
 * <pre>
 * 排序后: MetricFilter -> RegisteredTool
 * call 时: MetricFilter 记时 -> RegisteredTool 发 HTTP -> 返回 CallToolResult
 * </pre>
 *
 * @author alpha
 * @see ToolCallFilterChain
 * @see com.g2rain.mcp.registry.tool.ToolSpecBuilder
 * @since 2026/6/29
 */
@NullMarked
public final class ToolCallFilterChains {

    /**
     * 私有构造, 禁止实例化.
     */
    private ToolCallFilterChains() {

    }

    /**
     * 用完整 filter 列表构建链供应器.
     * <p>
     * 调用本方法时按 {@link ToolCallFilter#getOrder()} 排序一次; 返回的 {@link Supplier} 在每次
     * {@link Supplier#get()} 时创建新的链实例, 保证单次 call 内 {@code index} 从 0 递增.
     * </p>
     *
     * @param filters 全部 filter; 末级 filter 应声明 {@link ToolCallFilter#LOWEST_PRECEDENCE}
     * @return 链供应器, 供注册阶段保存并在每次 call 时 {@code get()}
     */
    public static Supplier<ToolCallFilterChain> of(ToolCallFilter terminalFilter, Collection<? extends ToolCallFilter> filters) {
        // 注册阶段 sort 一次, 后续 get() 复用同一有序列表, 末级 filter 与横切 filter 合并为完整链
        var orderedFilters = Stream.concat(
            filters.stream().sorted(AnnotationAwareOrderComparator.INSTANCE),
            Stream.of(terminalFilter)
        ).toList();

        return () -> new ToolCallFilterChain() {
            /** 当前 call 已执行到的 filter 下标, 每次 get() 新链从 0 开始. */
            int index;

            @Override
            public McpSchema.CallToolResult doFilter(McpRequestContext ctx, McpSchema.CallToolRequest req) {
                if (index >= orderedFilters.size()) {
                    // 所有 filter 均未返回结果, 链已耗尽
                    throw new BusinessException(McpErrorCode.TOOL_FILTER_CHAIN_EXHAUSTED);
                }

                // 取下一个 filter 执行; filter 可通过 chain.doFilter 驱动后续节点
                return orderedFilters.get(index++).doFilter(ctx, req, this);
            }
        };
    }
}
