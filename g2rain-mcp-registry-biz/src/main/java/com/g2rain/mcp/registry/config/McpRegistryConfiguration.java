package com.g2rain.mcp.registry.config;


import com.g2rain.mcp.registry.enricher.HttpRequestEnricherChain;
import com.g2rain.mcp.registry.error.DefaultErrorModelProvider;
import com.g2rain.mcp.registry.error.ErrorModelProvider;
import com.g2rain.mcp.registry.error.ErrorModelWriter;
import com.g2rain.mcp.registry.openapi.naming.EndpointStrategy;
import com.g2rain.mcp.registry.openapi.naming.NamingStrategy;
import com.g2rain.mcp.registry.support.MetricRecorder;
import com.g2rain.mcp.registry.support.ProgressUpdateProvider;
import com.g2rain.mcp.registry.support.metrics.NoOpMetricRecorder;
import com.g2rain.mcp.registry.support.progress.DefaultProgressUpdateProvider;
import com.g2rain.mcp.registry.tool.NotificationTicker;
import com.g2rain.mcp.registry.tool.ToolCallInterceptor;
import com.g2rain.mcp.registry.tool.ToolHandler;
import com.g2rain.mcp.registry.tool.ToolRestClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * g2rain MCP Registry 可替换扩展点的默认 Bean 装配.
 * <p>
 * 本类只注册「有默认实现、且允许业务方覆盖」的扩展点.
 * 若应用已声明同名类型的 Bean, 则不会重复注册（{@link ConditionalOnMissingBean}）.
 * </p>
 *
 * <h2>默认 Bean 一览</h2>
 * <ul>
 *   <li>{@link ErrorModelProvider} — 下游 HTTP 网络失败时的错误 JSON 结构（默认 {@link DefaultErrorModelProvider}）</li>
 *   <li>{@link NamingStrategy} — OpenAPI 操作 → MCP tool code 的命名规则（默认 {@link EndpointStrategy}）</li>
 *   <li>{@link MetricRecorder} — tool/HTTP 调用计数与耗时（默认 {@link NoOpMetricRecorder}, 不输出指标）</li>
 *   <li>{@link ProgressUpdateProvider} — 长耗时 call 的 MCP 进度通知文案（默认 {@link DefaultProgressUpdateProvider}）</li>
 *   <li>{@link ToolRestClients} — {@code tools/call} 出站 HTTP（{@code lb://} 负载均衡 + {@code http(s)://} 直连）</li>
 *   <li>{@link NotificationTicker} — 进度通知循环节拍（见 {@code g2rain.mcp.registry.progress-notification-interval-ms}）</li>
 *   <li>{@link ToolHandler} — MCP tool call 下游 HTTP 执行器</li>
 * </ul>
 *
 * <p>
 * 示例: 接入 Micrometer 时, 只需声明 {@code @Bean MetricRecorder micrometerMetricRecorder(...)},
 * 本配置类便不会注册 NoOp 实现.
 * </p>
 *
 * @author alpha
 * @see McpToolHandlerRegister
 * @since 2026/6/29
 */
@Configuration
public class McpRegistryConfiguration {

    @Value("${g2rain.mcp.registry.progress-notification-interval-ms:1}")
    private long progressNotificationIntervalMs;


    /**
     * 默认错误模型提供者: 按 HTTP 状态码返回 {@code error + description} 结构.
     */
    @Bean
    @ConditionalOnMissingBean(ErrorModelProvider.class)
    ErrorModelProvider<?> errorModelProvider() {
        return new DefaultErrorModelProvider();
    }

    /**
     * 默认 tool 命名策略: 按 {@code method + contextPath + path} 生成小写下划线 code.
     */
    @Bean
    @ConditionalOnMissingBean(NamingStrategy.class)
    NamingStrategy namingStrategy() {
        return new EndpointStrategy();
    }

    /**
     * 默认指标记录器: 空实现, 适合本地开发与未接入监控的环境.
     */
    @Bean
    @ConditionalOnMissingBean(MetricRecorder.class)
    MetricRecorder metricRecorder() {
        return new NoOpMetricRecorder();
    }

    /**
     * 默认进度通知提供器: 仅发送递增 tick, 不带 total 与文案.
     */
    @Bean
    @ConditionalOnMissingBean(ProgressUpdateProvider.class)
    ProgressUpdateProvider progressUpdateProvider() {
        return new DefaultProgressUpdateProvider();
    }

    /**
     * 负载均衡出站 HTTP 客户端构建器, 解析 {@code lb://service-id}.
     * <p>
     * 若容器已有其它 {@link LoadBalanced} 标注的 {@link RestClient.Builder}, 则跳过注册.
     * </p>
     */
    @Bean
    @LoadBalanced
    @ConditionalOnMissingBean(annotation = LoadBalanced.class)
    RestClient.Builder lbRestClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    /**
     * 按 endpoint scheme 在负载均衡与直连 {@link RestClient} 之间路由.
     */
    @Bean
    @ConditionalOnMissingBean(ToolRestClients.class)
    ToolRestClients toolRestClients(@LoadBalanced RestClient.Builder lbRestClientBuilder,
                                    RestClientBuilderConfigurer configurer,
                                    ToolCallInterceptor exchangeInterceptor) {
        RestClient lb = lbRestClientBuilder.requestInterceptor(exchangeInterceptor).build();
        RestClient direct = configurer.configure(RestClient.builder()).requestInterceptor(exchangeInterceptor).build();
        return new ToolRestClients(lb, direct);
    }

    /**
     * 默认进度通知节拍, 间隔见 {@code g2rain.mcp.registry.progress-notification-interval-ms}.
     */
    @Bean
    @ConditionalOnMissingBean(NotificationTicker.class)
    NotificationTicker notificationTicker() {
        return () -> Thread.sleep(progressNotificationIntervalMs);
    }

    /**
     * MCP {@code tools/call} 下游 HTTP 执行器.
     */
    @Bean
    @ConditionalOnMissingBean(ToolHandler.class)
    ToolHandler toolHandler(
        ToolRestClients restClients,
        ErrorModelWriter errorModelWriter,
        HttpRequestEnricherChain enricherChain,
        MetricRecorder metricRecorder,
        ProgressUpdateProvider progressUpdateProvider,
        NotificationTicker notificationTicker
    ) {
        return new ToolHandler(
            restClients,
            errorModelWriter,
            enricherChain,
            metricRecorder,
            progressUpdateProvider,
            notificationTicker
        );
    }
}
