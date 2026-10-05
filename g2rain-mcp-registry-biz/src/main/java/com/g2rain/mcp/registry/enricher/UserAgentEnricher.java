package com.g2rain.mcp.registry.enricher;


import com.g2rain.mcp.registry.context.McpRequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 为出站 HTTP 请求设置 {@code User-Agent} Header.
 * <p>
 * 标识「这次下游调用来自 g2rain MCP Registry」, 便于下游网关、日志、审计区分调用来源.
 * 不携带用户身份或鉴权信息, 仅作可观测性标记.
 * </p>
 *
 * <h2>当前固定值</h2>
 * <p>
 * {@code User-Agent: openapi-mcp}（见 {@link #enrich} 实现）.
 * 下游日志里看到该值, 即可判断请求经 MCP Registry 代理发出.
 * </p>
 *
 * <h2>执行顺序</h2>
 * <p>
 * {@code getOrder()} 返回 1000, intentionally 靠后执行, 避免覆盖其它 enricher 设置的 Header.
 * </p>
 *
 * @author alpha
 * @see HttpRequestEnricher
 * @see HttpRequestEnricherChain
 * @see PrincipalContextEnricher
 * @since 2026/6/30
 */
@Slf4j
@Component
public class UserAgentEnricher implements HttpRequestEnricher {

    /**
     * 追加固定 User-Agent, 标记请求来自 MCP Registry 代理.
     *
     * @param context MCP 请求上下文（本实现未使用, 保留扩展点）
     * @param spec    当前 RestClient 请求规格
     * @return 已设置 User-Agent 的请求规格
     */
    @Override
    public RestClient.RequestHeadersSpec<?> enrich(McpRequestContext context, RestClient.RequestHeadersSpec<?> spec) {
        return spec.header(HttpHeaders.USER_AGENT, "openapi-mcp");
    }

    @Override
    public int getOrder() {
        return 1000;
    }
}
