package com.g2rain.mcp.registry.enricher;


import com.g2rain.mcp.registry.context.McpRequestContext;
import com.g2rain.mcp.registry.tool.ToolHandler;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * {@link HttpRequestEnricher} 责任链, 按顺序对下游 HTTP 请求规格做增强.
 * <p>
 * <strong>在 call 链路中的位置:</strong>
 * </p>
 * <pre>
 * tools/call → SyncToolSpecification.callHandler → filter 链 → RegisteredTool
 *   → ToolHandler.execute → 组装 RestClient 请求 → 本类 enrich → 实际 HTTP 调用
 * </pre>
 * <p>
 * <strong>设计要点:</strong>
 * </p>
 * <ul>
 *   <li>每个 enricher 接收上一个 enricher 的输出, 返回新的 {@link RestClient.RequestHeadersSpec}</li>
 *   <li>单个 enricher 失败<strong>不阻断</strong>整次 tool call: 记录 warn 后继续后续 enricher</li>
 *   <li>仅负责可观测 / 非关键元数据; 鉴权等关键逻辑不在此链处理</li>
 * </ul>
 * <p>
 * 构造时传入的 enricher 列表在注册阶段确定 (通常由 Spring 注入所有 {@link HttpRequestEnricher} Bean,
 * 并按 {@link org.springframework.core.Ordered} 排序).
 * </p>
 *
 * @author alpha
 * @see HttpRequestEnricher
 * @see ToolHandler
 * @since 2026/6/30
 */
@Slf4j
@Component
@NullMarked
public class HttpRequestEnricherChain {

    /**
     * 已注册的 enricher 列表, 构造时拷贝为不可变列表, 保证链顺序稳定.
     */
    private final List<HttpRequestEnricher> enrichers;

    /**
     * 构造 enricher 责任链.
     *
     * @param enrichers 全部 {@link HttpRequestEnricher} Bean, 建议已按 {@link org.springframework.core.Ordered} 排序
     */
    public HttpRequestEnricherChain(List<HttpRequestEnricher> enrichers) {
        this.enrichers = enrichers.stream().sorted(AnnotationAwareOrderComparator.INSTANCE).toList();
    }

    /**
     * 依次应用所有 enricher, 返回最终增强后的请求规格.
     * <p>
     * 若某个 enricher 抛出 {@link RuntimeException}, 记录警告并跳过, 使用失败前的 {@code enrichedSpec} 继续链.
     * 保证 enricher 故障不会导致 MCP tool call 整体失败.
     * </p>
     *
     * @param context 当前 MCP 请求上下文, 传递给各 enricher 读取客户端 / HTTP 信息
     * @param spec    {@link ToolHandler} 组装好的初始 {@link RestClient} 请求规格
     * @return 增强后的请求规格, 可直接 {@code retrieve()} 发起下游调用
     */
    public RestClient.RequestHeadersSpec<?> enrich(McpRequestContext context, RestClient.RequestHeadersSpec<?> spec) {
        var enrichedSpec = spec;
        for (var enricher : enrichers) {
            try {
                log.debug("Applying enricher: {}", enricher.name());
                enrichedSpec = enricher.enrich(context, enrichedSpec);
            } catch (RuntimeException exception) {
                log.warn(
                    "Enricher {} failed during request enrichment: {}. Continuing with remaining enrichers.",
                    enricher.name(),
                    exception.getMessage()
                );
            }
        }

        return enrichedSpec;
    }
}
