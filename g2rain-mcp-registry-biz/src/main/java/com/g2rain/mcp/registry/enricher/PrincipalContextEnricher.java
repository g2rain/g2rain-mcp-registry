package com.g2rain.mcp.registry.enricher;


import com.g2rain.common.web.PrincipalContext;
import com.g2rain.common.web.PrincipalContextHolder;
import com.g2rain.mcp.registry.context.McpRequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Objects;

/**
 * 将当前登录用户的 Principal 上下文透传到下游 HTTP Header.
 * <p>
 * {@code tools/call} 会代理调用 g2rain 体系内的 OpenAPI 服务（如 basis）.
 * 下游服务需要知道「是谁在调用」, 本 enricher 从 {@link PrincipalContextHolder} 读取当前线程的
 * {@link PrincipalContext}, 把 userId、organId、traceId 等平台约定 Header 追加到出站请求.
 * </p>
 *
 * <h2>Header 来源</h2>
 * <p>
 * 调用 {@link PrincipalContext#getHeaders()}, 字段定义见 {@link com.g2rain.common.web.PrincipalHeaders}.
 * </p>
 *
 * <h2>前置条件</h2>
 * <p>
 * 入站 MCP HTTP 请求需先经过 {@code PrincipalContextScopeFilter}、{@code PrincipalContextFilter},
 * 把网关/客户端传来的 Principal Header 解析并绑定到 {@link PrincipalContextHolder}.
 * 若当前线程无上下文（非 Web 线程、Filter 未生效等）, 本 enricher 静默跳过, 不追加 Header.
 * </p>
 *
 * <h2>执行顺序</h2>
 * <p>
 * {@code getOrder() = 100}, 早于 {@link UserAgentEnricher}(1000),
 * 确保身份/租户/链路 Header 先于可观测性 Header 写入.
 * </p>
 *
 * <h2>与鉴权的边界</h2>
 * <p>
 * 本 enricher 只<strong>透传</strong>已有上下文, 不负责获取或校验凭证.
 * OpenAPI 里定义的 Authorization 等安全 Header 仍由 {@link com.g2rain.mcp.registry.tool.ToolHandler} 按 arguments 显式设置.
 * </p>
 *
 * @author alpha
 * @see HttpRequestEnricher
 * @see HttpRequestEnricherChain
 * @see PrincipalContextHolder
 * @since 2026/7/1
 */
@Slf4j
@Component
public class PrincipalContextEnricher implements HttpRequestEnricher {

    /**
     * 读取当前线程 Principal 上下文, 将其 Header 逐条追加到出站 RestClient 请求.
     * <p>
     * 处理流程:
     * </p>
     * <ol>
     *   <li>从 {@link PrincipalContextHolder#get()} 获取 {@link PrincipalContext}</li>
     *   <li>若上下文不存在, 直接返回入参 {@code spec}, 不做任何修改</li>
     *   <li>遍历 {@link PrincipalContext#getHeaders()} 返回的 Map, 对每个 entry 调用 {@code spec.header(key, values)}</li>
     *   <li>返回累积增强后的 {@code spec}, 供链中后续 enricher 继续处理</li>
     * </ol>
     * <p>
     * {@code context} 参数当前未使用, 保留以便后续按 MCP 会话 / 客户端信息做差异化透传.
     * </p>
     *
     * @param context MCP 请求上下文, 预留扩展点 (当前实现未读取)
     * @param spec    当前 RestClient 请求规格, 可能已被前置 enricher 修改
     * @return 已追加 Principal Header 的请求规格; 无上下文时返回原 {@code spec}
     */
    @Override
    public RestClient.RequestHeadersSpec<?> enrich(McpRequestContext context, RestClient.RequestHeadersSpec<?> spec) {
        // 读取当前请求的 Principal 上下文
        PrincipalContext principalContext = PrincipalContextHolder.get();
        if (Objects.isNull(principalContext)) {
            return spec;
        }

        // RestClient.header 每次调用返回新 spec, 需逐条累积
        var enrichedSpec = spec;
        // getHeaders() 的 value 为 Collection<String>, RestClient 需要 String[] 变参
        for (var e : principalContext.getHeaders().entrySet()) {
            enrichedSpec = enrichedSpec.header(e.getKey(), e.getValue().toArray(String[]::new));
        }

        return enrichedSpec;
    }

    /**
     * 返回本 enricher 在 {@link HttpRequestEnricherChain} 中的执行顺序.
     *
     * @return 100, 值越小越先执行
     */
    @Override
    public int getOrder() {
        return 100;
    }
}
