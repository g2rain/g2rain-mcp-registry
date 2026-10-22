package com.g2rain.mcp.registry.support;



import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.support.metrics.NoOpMetricRecorder;
import org.springframework.http.HttpStatusCode;

/**
 * MCP 工具调用与下游 HTTP 调用的指标记录抽象.
 * <p>
 * 供 {@link com.g2rain.mcp.registry.tool.ToolHandler} 在 tool 执行生命周期内上报:
 * </p>
 * <ul>
 *   <li><strong>计数</strong> — 每次 tool 调用、每次 HTTP 请求各记一次</li>
 *   <li><strong>耗时</strong> — 通过 {@link #startTimer()} 获取 {@link Timer}, 在调用结束点记录 elapsed time</li>
 * </ul>
 * <p>
 * 典型用法:
 * </p>
 * <pre>{@code
 * metricRecorder.recordToolCall(operation);
 * var httpTimer = metricRecorder.startTimer();
 * var toolTimer = metricRecorder.startTimer();
 * try {
 *     var response = executeHttp(...);
 *     httpTimer.timeHttpCall(operation, response.getStatusCode());
 *     metricRecorder.recordHttpCall(operation, response.getStatusCode());
 *     toolTimer.timeToolCall(operation, response.getStatusCode().isError());
 * } catch (HttpStatusCodeException ex) {
 *     httpTimer.timeHttpCall(operation, ex.getStatusCode());
 *     metricRecorder.recordHttpCall(operation, ex.getStatusCode());
 *     toolTimer.timeToolCall(operation, true);
 * }
 * }</pre>
 * <p>
 * 默认无操作实现为 {@link NoOpMetricRecorder},
 * 生产环境可替换为 Micrometer/Prometheus 等具体实现.
 * </p>
 *
 * @author alpha
 * @since 2026/6/30
 * @see NoOpMetricRecorder
 * @see com.g2rain.mcp.registry.catalog.OpenApiOperation
 */
public interface MetricRecorder {

    /**
     * 单次 tool 或 HTTP 调用的耗时记录器.
     * <p>
     * 由 {@link MetricRecorder#startTimer()} 创建, 在调用结束点调用对应 {@code time*} 方法写入耗时分布.
     * 同一 {@link Timer} 实例只应记录一种维度的耗时 (tool 整段 vs 纯 HTTP 往返).
     * </p>
     */
    interface Timer {

        /**
         * 记录一次 tool 调用的端到端耗时.
         * <p>
         * 从 tool 入口到返回 {@link io.modelcontextprotocol.spec.McpSchema.CallToolResult} 的 wall time,
         * 含 enricher、HTTP、序列化等全部步骤.
         * </p>
         *
         * @param openApiOperation 被调用的 OpenAPI 操作, 用作指标 tag (如 operationId、method、path)
         * @param isError          本次 tool 调用是否以错误结束 (HTTP 4xx/5xx 或业务 isError=true)
         */
        void timeToolCall(OpenApiOperation openApiOperation, boolean isError);

        /**
         * 记录一次下游 HTTP 请求的往返耗时.
         * <p>
         * 仅覆盖 {@code RestClient} 发出请求到收到响应的时间, 不含 MCP 层包装开销.
         * </p>
         *
         * @param openApiOperation 被调用的 OpenAPI 操作
         * @param httpStatusCode   HTTP 响应状态码, 用于 success/error 分桶
         */
        void timeHttpCall(OpenApiOperation openApiOperation, HttpStatusCode httpStatusCode);
    }

    /**
     * 递增 tool 调用总次数计数器.
     * <p>
     * 应在 HTTP 请求发出前调用, 确保即使后续失败也能统计到「已尝试的 tool 调用」.
     * </p>
     *
     * @param openApiOperation 被调用的 OpenAPI 操作
     */
    void recordToolCall(OpenApiOperation openApiOperation);

    /**
     * 递增 HTTP 调用总次数计数器, 并按状态码分桶.
     * <p>
     * 在收到 HTTP 响应 (含 4xx/5xx 异常路径) 后调用, 与 {@link Timer#timeHttpCall} 配对使用.
     * </p>
     *
     * @param openApiOperation 被调用的 OpenAPI 操作
     * @param httpStatusCode   HTTP 响应状态码
     */
    void recordHttpCall(OpenApiOperation openApiOperation, HttpStatusCode httpStatusCode);

    /**
     * 创建新的耗时记录器, 调用方在适当时机调用 {@link Timer} 方法提交 elapsed time.
     * <p>
     * 每次 tool 执行通常创建两个 timer: 一个测 HTTP, 一个测整段 tool 调用.
     * </p>
     *
     * @return 新的 {@link Timer} 实例; 具体实现可能绑定 Micrometer {@code Timer.Sample} 等
     */
    Timer startTimer();
}
