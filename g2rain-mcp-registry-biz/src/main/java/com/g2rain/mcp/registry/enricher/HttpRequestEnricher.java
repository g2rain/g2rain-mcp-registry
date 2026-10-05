package com.g2rain.mcp.registry.enricher;


import com.g2rain.mcp.registry.context.McpRequestContext;
import com.g2rain.mcp.registry.tool.ToolHandler;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;

/**
 * 下游 HTTP 请求的增强器, 在 {@link ToolHandler} 发出真实 API 调用前, 对 {@link RestClient.RequestHeadersSpec} 做横切补充.
 * <p>
 * <strong>用途:</strong> 统一追加可观测性 / 链路相关的 Header 或元数据 (如 User-Agent, X-Forwarded-For 等),
 * 避免在每个 tool 调用路径里重复写 Header 逻辑.
 * </p>
 * <p>
 * <strong>不适用场景:</strong> 鉴权, 租户凭证等<strong>安全关键</strong>逻辑不应放在 enricher 中,
 * 应在 {@link ToolHandler} 内显式处理, 失败即中断, 不做静默降级.
 * </p>
 * <p>
 * <strong>调用时机:</strong> {@code tools/call} → filter 链 → {@link ToolHandler#execute} 组装完 path/method/body 后,
 * {@code retrieve()} 之前, 由 {@link HttpRequestEnricherChain#enrich} 依次应用所有已注册 enricher.
 * </p>
 * <p>
 * 实现类注册为 Spring Bean 后, 由容器注入 {@link HttpRequestEnricherChain}; 通过 {@link #getOrder()} 控制执行顺序
 * (值越小越先执行).
 * </p>
 *
 * @see HttpRequestEnricherChain
 * @see McpRequestContext
 * @see ToolHandler
 * @author alpha
 * @since 2026/6/30
 */
public interface HttpRequestEnricher extends Ordered {

    /**
     * 对即将发出的下游 HTTP 请求做增强 (追加 Header 等).
     *
     * @param context 当前 MCP 请求上下文, 可读取 {@code httpServletRequest}, {@code syncServerExchange} 等
     * @param spec    当前 {@link RestClient} 请求规格, 可能已被前置 enricher 修改
     * @return 增强后的请求规格, 供链中下一个 enricher 或 {@link ToolHandler} 继续执行
     */
    RestClient.RequestHeadersSpec<?> enrich(McpRequestContext context, RestClient.RequestHeadersSpec<?> spec);

    /**
     * 返回 enricher 名称, 默认使用实现类简单类名, 供日志标识.
     *
     * @return enricher 名称
     */
    default String name() {
        return this.getClass().getSimpleName();
    }

    /**
     * 返回 enricher 在链中的排序值, 默认 {@link Ordered#LOWEST_PRECEDENCE}.
     * <p>
     * 实现类应覆盖本方法并声明明确的 ORDER 常量, 避免多个 enricher 顺序不确定.
     * </p>
     *
     * @return 排序值, 越小越先执行
     */
    @Override
    default int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
