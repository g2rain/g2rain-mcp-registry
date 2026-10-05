package com.g2rain.mcp.registry.tool;


import com.g2rain.mcp.registry.context.McpRequestContexts;
import com.g2rain.mcp.registry.tool.filter.RegisteredTool;
import com.g2rain.mcp.registry.tool.filter.ToolCallFilter;
import com.g2rain.mcp.registry.tool.filter.ToolCallFilterChain;
import com.g2rain.mcp.registry.tool.filter.ToolCallFilterChains;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * 将 {@link RegisteredTool} 组装为可缓存、可执行的 {@code SyncToolSpecification}.
 * <p>
 * 在 Tool <strong>注册阶段</strong>（非每次 call）由 {@link ToolRegistry} 调用.
 * 产出结果存入 {@link ResolvedTool#toolSpec()}, 供 list 读元数据、call 执行 {@code callHandler}.
 * </p>
 *
 * <h2>组装内容</h2>
 * <ol>
 *   <li>{@code tool} — MCP 元数据（name、title、inputSchema）, 供 {@code tools/list} 返回</li>
 *   <li>{@code callHandler} — 每次 {@code tools/call} 时执行的闭包, 内部走 Filter 责任链</li>
 * </ol>
 *
 * <h2>Filter 链组装</h2>
 * <pre>
 * ToolCallFilterChains.of(RegisteredTool, 横切Filters)
 *   -> Supplier&lt;ToolCallFilterChain&gt;   （注册时 sort 一次）
 * 每次 call:
 *   chainSupplier.get().doFilter(context, request)
 *     -> Filter1 -> Filter2 -> ... -> RegisteredTool -> ToolHandler
 * </pre>
 *
 * <h2>示例</h2>
 * <p>
 * 注册 {@code get_order_by_id} 时, 假设容器里有一个指标 Filter:
 * </p>
 * <ul>
 *   <li>注册阶段: 把 RegisteredTool 与指标 Filter 合并排序, 生成 chainSupplier</li>
 *   <li>call 阶段: 每次请求 {@code get()} 新链, 先走指标 Filter, 最后到 RegisteredTool 发 HTTP</li>
 * </ul>
 *
 * <h2>职责边界</h2>
 * <ul>
 *   <li><strong>负责</strong>: {@code SyncToolSpecification} 的 tool + callHandler 组装</li>
 *   <li><strong>不负责</strong>: profile 加载、权限过滤、缓存索引（{@link ToolRegistry}）</li>
 * </ul>
 *
 * @author alpha
 * @see ToolCallFilterChains
 * @see ResolvedTool
 * @see ToolRegistry#put(ResolvedTool)
 * @since 2026/7/1
 */
@Component
@NullMarked
public class ToolSpecBuilder {

    /**
     * 横切 {@link ToolCallFilter} 列表 (鉴权, 指标, mock 等), 由 Spring 容器注入所有 {@link ToolCallFilter} Bean.
     * <p>
     * 不含 {@link RegisteredTool} (末级 filter 由 {@link #buildSyncToolSpec(RegisteredTool)} 入参传入).
     * </p>
     */
    private final List<ToolCallFilter> filters;

    /**
     * 构造 Tool Spec 构建器.
     *
     * @param filters 容器中全部横切 filter; 无 Bean 时为空列表, 链中仅含 {@link RegisteredTool}
     */
    public ToolSpecBuilder(List<ToolCallFilter> filters) {
        this.filters = filters;
    }

    /**
     * 将 {@link RegisteredTool} 构建为 {@link McpServerFeatures.SyncToolSpecification}.
     * <p>
     * {@code callHandler} closure 内 capture {@code chainSupplier}, 保证 {@link ToolCallFilterChains#of} 仅在
     * 本方法调用时执行一次; 每次 {@code tools/call} 仅 {@code chainSupplier.get()} 获取新链实例.
     * </p>
     *
     * @param registeredTool 含 MCP tool 元数据与 {@link RegisteredTool#toolHandlerFunc()} 的末级 filter
     * @return 可注册 / 缓存的 {@code SyncToolSpecification}, 供 {@link McpToolCallHandler} 通过 {@code callHandler().apply(...)} 执行
     */
    public McpServerFeatures.SyncToolSpecification buildSyncToolSpec(RegisteredTool registeredTool) {
        // 注册阶段 of 一次: RegisteredTool + 横切 filters → 有序链供应器
        Supplier<ToolCallFilterChain> chainSupplier = ToolCallFilterChains.of(registeredTool, filters);
        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(registeredTool.tool())
            .callHandler((exchange, request) -> {
                var context = McpRequestContexts.of(exchange, request);
                // 每次 call 新建链实例 (index 从 0 开始), 不可跨请求复用
                return chainSupplier.get().doFilter(context, request);
            }).build();
    }
}
