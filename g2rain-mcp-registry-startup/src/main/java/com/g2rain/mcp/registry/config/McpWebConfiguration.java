package com.g2rain.mcp.registry.config;


import com.g2rain.common.exception.ExceptionProcessor;
import com.g2rain.mcp.registry.web.McpGlobalExceptionFilter;
import com.g2rain.mcp.registry.web.McpHandlerFilter;
import com.g2rain.web.autoconfigure.G2rainWebProperties;
import com.g2rain.web.filters.GlobalExceptionFilter;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStatelessAutoConfiguration;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * <p>{@code McpWebConfiguration} 是 MCP Registry 服务的 Web 传输层配置类。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *     <li>以与 Spring AI 相同的 Bean 名注册带 {@link McpHandlerFilter} 的 {@link RouterFunction}，
 *         借助 {@code @ConditionalOnMissingBean(name = ...)} 使 Spring AI 跳过无 Filter 的默认路由。</li>
 *     <li>注册 {@link McpGlobalExceptionFilter}，使 MCP 端点异常以 JSON-RPC error 格式返回。</li>
 *     <li>Transport（{@link WebMvcStreamableServerTransportProvider} / {@link WebMvcStatelessServerTransport}）
 *         仍由 Spring AI 自动配置创建，本类不重复注册。</li>
 * </ul>
 *
 * @see McpHandlerFilter
 * @see McpGlobalExceptionFilter
 */
@Configuration
public class McpWebConfiguration {

    /**
     * MCP HTTP 端点路径，用于 {@link McpGlobalExceptionFilter} 识别 MCP 请求。
     * <p>绑定配置项 {@code spring.ai.mcp.server.streamable-http.mcp-endpoint}，默认 {@code /mcp}。</p>
     */
    @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}")
    private String mcpEndpoint;

    /**
     * 注册 MCP 专用全局异常过滤器，替换默认的 {@link GlobalExceptionFilter}。
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    GlobalExceptionFilter globalExceptionFilter(ExceptionProcessor exceptionProcessor, G2rainWebProperties properties) {
        return new McpGlobalExceptionFilter(exceptionProcessor, properties, mcpEndpoint);
    }

    /**
     * 注册 MCP RouterFunction 登录态校验过滤器。
     */
    @Bean
    McpHandlerFilter mcpHandlerFilter() {
        return new McpHandlerFilter();
    }

    /**
     * Streamable HTTP 模式：注册带登录校验 Filter 的 RouterFunction。
     * <p>Bean 名 {@code webMvcStreamableServerRouterFunction} 与 Spring AI 一致，
     * 不使用 {@code @ConditionalOnBean}，避免用户配置先于 auto-config 解析时条件不满足而被跳过。</p>
     */
    @Configuration
    @Conditional(McpServerAutoConfiguration.EnabledStreamableServerCondition.class)
    static class Streamable {

        @Bean
        RouterFunction<ServerResponse> webMvcStreamableServerRouterFunction(
            WebMvcStreamableServerTransportProvider provider,
            McpHandlerFilter mcpHandlerFilter
        ) {
            return provider.getRouterFunction().filter(mcpHandlerFilter);
        }
    }

    /**
     * Stateless HTTP 模式：注册带登录校验 Filter 的 RouterFunction。
     */
    @Configuration
    @Conditional(McpServerStatelessAutoConfiguration.EnabledStatelessServerCondition.class)
    static class Stateless {

        @Bean
        RouterFunction<ServerResponse> webMvcStatelessServerRouterFunction(
            WebMvcStatelessServerTransport transport,
            McpHandlerFilter mcpHandlerFilter
        ) {
            return transport.getRouterFunction().filter(mcpHandlerFilter);
        }
    }
}
