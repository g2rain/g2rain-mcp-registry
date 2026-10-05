package com.g2rain.mcp.registry.support.metrics;


import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.support.MetricRecorder;
import org.springframework.http.HttpStatusCode;

/**
 * {@link MetricRecorder} 的空实现 (No-Op).
 * <p>
 * 所有计数与耗时方法均为空操作, 不产生任何指标输出.
 * 用于:
 * </p>
 * <ul>
 *   <li>未集成 Micrometer/监控后端的默认 Spring Boot 自动配置</li>
 *   <li>单元测试与本地开发, 避免依赖真实指标基础设施</li>
 *   <li>显式关闭 observability 时的占位 Bean</li>
 * </ul>
 * <p>
 * {@link #startTimer()} 返回的匿名 {@link MetricRecorder.Timer} 同样为空实现,
 * 调用方仍可按生产代码相同的路径调用 {@code timeToolCall}/{@code timeHttpCall}, 无需 null 检查.
 * </p>
 *
 * @author alpha
 * @since 2026/6/30
 * @see MetricRecorder
 */
public class NoOpMetricRecorder implements MetricRecorder {

    /**
     * 创建新的耗时记录器。
     * <p>
     * 本实现返回不记录任何数据的 {@link Timer} 实例，可安全多次调用，
     * 满足调用方对 timer 非 null 的约定。
     * </p>
     *
     * @return 空操作 {@link Timer}；{@code timeToolCall}/{@code timeHttpCall} 均为 no-op
     */
    @Override
    public Timer startTimer() {
        // 匿名 Timer: 满足 ToolHandler 对 timer 的非 null 约定, 但不写入任何 backend
        return new Timer() {

            /**
             * 记录一次 tool 调用的端到端耗时。
             * <p>空实现，忽略 operation 与 isError，不写入任何 backend。</p>
             *
             * @param openApiOperation 被调用的 OpenAPI 操作
             * @param isError          本次 tool 调用是否以错误结束
             */
            @Override
            public void timeToolCall(OpenApiOperation openApiOperation, boolean isError) {
                // no-op
            }

            /**
             * 记录一次下游 HTTP 请求的往返耗时。
             * <p>空实现，忽略 operation 与 statusCode，不写入任何 backend。</p>
             *
             * @param openApiOperation 被调用的 OpenAPI 操作
             * @param httpStatusCode   HTTP 响应状态码
             */
            @Override
            public void timeHttpCall(OpenApiOperation openApiOperation, HttpStatusCode httpStatusCode) {
                // no-op
            }
        };
    }

    /**
     * 递增 tool 调用总次数计数器。
     * <p>空实现，不递增任何 counter。</p>
     *
     * @param openApiOperation 被调用的 OpenAPI 操作
     */
    @Override
    public void recordToolCall(OpenApiOperation openApiOperation) {
        // no-op
    }

    /**
     * 递增 HTTP 调用总次数计数器，并按状态码分桶。
     * <p>空实现，不按 statusCode 分桶。</p>
     *
     * @param openApiOperation 被调用的 OpenAPI 操作
     * @param httpStatusCode   HTTP 响应状态码
     */
    @Override
    public void recordHttpCall(OpenApiOperation openApiOperation, HttpStatusCode httpStatusCode) {
        // no-op
    }
}
