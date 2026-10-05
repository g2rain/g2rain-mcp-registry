package com.g2rain.mcp.registry.tool;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.context.McpRequestContext;
import com.g2rain.mcp.registry.enricher.HttpRequestEnricherChain;
import com.g2rain.mcp.registry.error.ErrorModelWriter;
import com.g2rain.mcp.registry.schema.HttpRequestSpec;
import com.g2rain.mcp.registry.support.MetricRecorder;
import com.g2rain.mcp.registry.support.ProgressUpdateProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * MCP {@code tools/call} 的下游 HTTP 执行器: 将已分解的请求参数转为真实 HTTP 调用, 并包装为 {@link McpSchema.CallToolResult}.
 * <p>
 * <strong>职责:</strong>
 * </p>
 * <ul>
 *   <li>按 {@link OpenApiOperation} 的 endpoint + path + method 组装出站 URL (支持 {@code lb://} 与 {@code http://})</li>
 *   <li>应用 {@link HttpRequestSpec} 中的 path / query / header / JSON body</li>
 *   <li>经 {@link HttpRequestEnricherChain} 追加 Principal、User-Agent 等横切 Header</li>
 *   <li>记录 {@link MetricRecorder} 指标, 可选发送 MCP 进度通知</li>
 *   <li>将 HTTP 响应或错误映射为 {@link McpSchema.CallToolResult}</li>
 * </ul>
 * <p>
 * <strong>调用链:</strong>
 * </p>
 * <pre>
 * tools/call → {@link com.g2rain.mcp.registry.tool.filter.ToolCallFilter} 链
 *     → {@link com.g2rain.mcp.registry.tool.filter.RegisteredTool} → {@link #execute} → {@link #doExecute}
 * </pre>
 * <p>
 * <strong>设计要点:</strong>
 * </p>
 * <ul>
 *   <li>endpoint 每次 call 动态拼接, 不设 RestClient {@code baseUrl}</li>
 *   <li>鉴权通过 {@link com.g2rain.mcp.registry.enricher.PrincipalContextEnricher} 透传 Principal Header</li>
 *   <li>不含 JSON 双重序列化自动重试</li>
 *   <li>网络失败时使用 {@link ErrorModelWriter} 生成平台统一错误 JSON</li>
 * </ul>
 * <p>
 * <strong>错误处理三分支:</strong>
 * </p>
 * <ol>
 *   <li>2xx — 透传响应体 (空 body 时使用 {@link #DEFAULT_SUCCESS_RESPONSE})</li>
 *   <li>{@link org.springframework.web.client.HttpStatusCodeException} — 透传下游 4xx/5xx 响应体</li>
 *   <li>{@link RuntimeException} — 连接失败等, 返回 {@link HttpStatus#BAD_GATEWAY} 结构化错误</li>
 * </ol>
 *
 * @author alpha
 * @see HttpRequestSpec
 * @see OpenApiOperation
 * @see HttpRequestEnricherChain
 * @see ErrorModelWriter
 * @since 2026/6/30
 */
@Slf4j
public class ToolHandler {

    /**
     * 出站请求首选 Accept 类型, 与 {@link HttpRequestSpec#SUPPORTED_MEDIA_TYPE} 一致.
     */
    private static final MediaType PREFERRED_ACCEPT_MEDIA_TYPE = MediaType.APPLICATION_JSON;

    /**
     * 下游返回 2xx 但 body 为空时的默认成功 JSON, 避免 MCP 客户端收到空 content.
     */
    private static final String DEFAULT_SUCCESS_RESPONSE = "{\"message\":\"Tool call completed successfully\"}";

    /**
     * 出站 HTTP 客户端选择器: {@code lb://} 走负载均衡, {@code http(s)://} 直连.
     */
    private final ToolRestClients restClients;

    /**
     * 网络层失败 (未拿到 HTTP 响应) 时的错误 JSON 生成器.
     */
    private final ErrorModelWriter errorModelWriter;

    /**
     * 出站请求增强链 (Principal 透传、User-Agent 等).
     */
    private final HttpRequestEnricherChain enricherChain;

    /**
     * tool 调用与 HTTP 调用计数 / 耗时指标.
     */
    private final MetricRecorder metricRecorder;

    /**
     * 长耗时下游调用期间的 MCP 进度通知内容提供器.
     */
    private final ProgressUpdateProvider progressUpdateProvider;

    /**
     * 进度通知循环的「节拍器」, 控制两次 notification 之间的等待间隔.
     */
    private final NotificationTicker ticker;

    public ToolHandler(
        ToolRestClients restClients,
        ErrorModelWriter errorModelWriter,
        HttpRequestEnricherChain enricherChain,
        MetricRecorder metricRecorder,
        ProgressUpdateProvider progressUpdateProvider,
        NotificationTicker ticker
    ) {
        this.restClients = restClients;
        this.errorModelWriter = errorModelWriter;
        this.enricherChain = enricherChain;
        this.metricRecorder = metricRecorder;
        this.progressUpdateProvider = progressUpdateProvider;
        this.ticker = ticker;
    }

    /**
     * MCP {@code tools/call} 的<strong>编排入口</strong>: 负责指标、异常分流, 并委托 {@link #doExecute} 发出真实 HTTP.
     * <p>
     * <strong>输入来源:</strong>
     * </p>
     * <ul>
     *   <li>{@code context} — MCP 会话上下文 (进度通知回调、原始 CallToolRequest 等)</li>
     *   <li>{@code httpRequestSpec} — {@link com.g2rain.mcp.registry.schema.InputSchemaComposer#decompose} 将 LLM 传入的 arguments
     *       按 OpenAPI 分解后的 path / query / header / JSON body</li>
     *   <li>{@code operation} — 目标 API 元数据: {@link OpenApiOperation#endpoint()} (如 {@code lb://g2rain-basis}),
     *       {@link OpenApiOperation#path()} (如 {@code /resource_api/page}), HTTP method</li>
     * </ul>
     * <p>
     * <strong>输出:</strong> {@link McpSchema.CallToolResult}, 正文为 JSON 字符串 ({@code TextContent}),
     * {@code isError} 告知 MCP 客户端本次 tool 是否失败.
     * </p>
     * <p>
     * <strong>完整执行流程:</strong>
     * </p>
     * <ol>
     *   <li>{@link MetricRecorder#recordToolCall} — MCP tool 层调用计数 +1</li>
     *   <li>启动两个独立 timer (见方法内注释: HTTP 耗时 vs tool 总耗时)</li>
     *   <li>{@link #doExecute} — 拼 URL、设 header/body、enrich、{@code RestClient.retrieve()}</li>
     *   <li>根据结果进入三条分支之一 (见下方), 停止 timer、记 HTTP 计数、构造 {@code CallToolResult}</li>
     * </ol>
     * <p>
     * <strong>三条结果分支:</strong>
     * </p>
     * <ul>
     *   <li><strong>正常返回 {@link ResponseEntity}</strong> — RestClient 收到 2xx (默认 4xx/5xx 会走 catch, 故此处通常为成功).
     *       透传 body; 空 body 用 {@link #DEFAULT_SUCCESS_RESPONSE}. {@code isError} 取自 status (实际多为 false).</li>
     *   <li><strong>{@link HttpStatusCodeException}</strong> — 下游返回 4xx/5xx, RestClient 抛异常, 响应体在
     *       {@code exception.getResponseBodyAsString()}. 原样透传给 LLM, {@code isError=true}.</li>
     *   <li><strong>其他 {@link RuntimeException}</strong> — 连接失败、超时、DNS、lb 不可达等, 无有效 HTTP 响应.
     *       指标记 502, 正文由 {@link ErrorModelWriter} 生成平台统一错误 JSON.</li>
     * </ul>
     * <p>
     * <strong>不负责:</strong> MCP 参数分解 ({@link HttpRequestSpec} 由上游完成)、权限校验 (filter 链)、
     * JSON 双重序列化自动重试 (本模块未实现).
     * </p>
     *
     * @param context         MCP 请求上下文
     * @param httpRequestSpec 已分解的 HTTP 请求参数
     * @param operation       目标 OpenAPI 操作 (endpoint + path + method)
     * @return 供 MCP SDK 返回给客户端的 tool 结果
     */
    public McpSchema.CallToolResult execute(McpRequestContext context, HttpRequestSpec httpRequestSpec, OpenApiOperation operation) {
        // MCP tool 维度计数: 「某个 operation 被 call 了一次」(不区分 HTTP 成败)
        metricRecorder.recordToolCall(operation);

        // HTTP 专用 timer: 仅度量 doExecute 内 RestClient.retrieve() 的阻塞时间 (不含 execute 内其它逻辑)
        var httpCallTimer = metricRecorder.startTimer();
        // Tool 专用 timer: 度量整个 execute 方法 wall time (含 doExecute + 指标上报 + 结果包装)
        var toolCallTimer = metricRecorder.startTimer();

        try {
            // === 分支 A 前置: 发起下游 HTTP, 2xx 时正常返回 ResponseEntity ===
            var response = doExecute(context, httpRequestSpec, operation);

            // --- 指标: HTTP 层 ---
            // 停止 HTTP timer, 上报 RestClient 往返耗时 (按 operation + statusCode 分桶)
            httpCallTimer.timeHttpCall(operation, response.getStatusCode());
            // HTTP 出站计数 +1 (一次 retrieve 对应一次 recordHttpCall)
            metricRecorder.recordHttpCall(operation, response.getStatusCode());

            // --- 指标: Tool 层 ---
            // 停止 tool timer; 2xx 时 isError=false, 表示 MCP 层视为成功
            toolCallTimer.timeToolCall(operation, response.getStatusCode().isError());

            // --- 构造 MCP 成功结果 ---
            // addTextContent: 新版 MCP SDK 要求 List<Content>, 此处为下游 JSON 文本
            return McpSchema.CallToolResult.builder()
                .addTextContent(getResponseBodyOrDefault(response.getBody()))
                .isError(response.getStatusCode().isError())
                .build();

        } catch (HttpStatusCodeException exception) {
            // === 分支 B: 下游有 HTTP 响应, 但 status 为 4xx/5xx ===
            // (RestClient.retrieve() 默认对错误 status 抛异常而非返回 ResponseEntity)

            httpCallTimer.timeHttpCall(operation, exception.getStatusCode());
            metricRecorder.recordHttpCall(operation, exception.getStatusCode());
            // 记录下游原始错误 body, 便于排查 (不打印到 MCP 客户端, 客户端拿 exception body)
            log.debug("HTTP status code {}: {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            toolCallTimer.timeToolCall(operation, true);

            // 透传下游错误 body (通常是该服务自己的 error JSON), LLM 可据此修正参数
            return McpSchema.CallToolResult.builder()
                .addTextContent(exception.getResponseBodyAsString())
                .isError(true)
                .build();

        } catch (RuntimeException e) {
            // === 分支 C: 网络层失败, 未拿到 HTTP 响应 ===
            // 例: Connection refused, Read timed out, UnknownHost, lb://  scheme 未配置 LoadBalancer

            // 无真实 statusCode, 统一按 502 Bad Gateway 记入指标
            httpCallTimer.timeHttpCall(operation, HttpStatus.BAD_GATEWAY);
            metricRecorder.recordHttpCall(operation, HttpStatus.BAD_GATEWAY);
            log.error("Error while calling tool: {}. Downstream request failed.", e.getMessage(), e);
            toolCallTimer.timeToolCall(operation, true);

            // 不暴露内部 stack trace 给 LLM; 使用 ErrorModelWriter 生成固定结构的 error JSON
            return McpSchema.CallToolResult.builder()
                .addTextContent(errorModelWriter.writeErrorModelAsJson(HttpStatus.BAD_GATEWAY))
                .isError(true)
                .build();
        }
    }

    /**
     * 响应体为空时使用默认成功 JSON.
     *
     * @param responseBody 下游 HTTP 响应体, 可为 {@code null}
     * @return 非空字符串
     */
    private String getResponseBodyOrDefault(@Nullable String responseBody) {
        // 204 No Content 或 body 为 null 时, 给 MCP 客户端一个非空默认 JSON
        return Objects.toString(responseBody, DEFAULT_SUCCESS_RESPONSE);
    }

    /**
     * 组装并<strong>同步发送</strong>单次下游 HTTP 请求, 返回原始 {@link ResponseEntity}.
     * <p>
     * <strong>与 {@link #execute} 的分工:</strong> 本方法只做「HTTP 怎么发」, 不做 MCP 结果包装、不做三分支异常策略
     * (4xx 会直接抛 {@link HttpStatusCodeException} 给 execute 处理).
     * </p>
     * <p>
     * <strong>URL 组装 (无 RestClient baseUrl):</strong>
     * </p>
     * <pre>
     * 最终 URI = operation.endpoint() + operation.path() + query + path变量替换
     * 例: lb://g2rain-basis + /resource_api/{id} + ?page=1 → lb://g2rain-basis/resource_api/123?page=1
     * </pre>
     * <p>
     * <strong>五个阶段:</strong>
     * </p>
     * <ol>
     *   <li><strong>Method + URI</strong> — {@link RestClient#method} + {@link UriComponentsBuilder} 动态 endpoint</li>
     *   <li><strong>Body</strong> — 若有 JSON requestBody, 设置 content 与 Content-Type</li>
     *   <li><strong>OpenAPI Header</strong> — 来自 tool arguments 的 header 参数</li>
     *   <li><strong>Enricher</strong> — {@link HttpRequestEnricherChain} 追加 Principal、User-Agent 等; 补 Accept</li>
     *   <li><strong>Retrieve</strong> — 同步 HTTP; 若客户端支持 progressToken, 另启虚拟线程推送进度</li>
     * </ol>
     * <p>
     * <strong>进度通知:</strong> 需同时满足 progressNotification 回调、CallToolRequest、progressToken 非空.
     * 主线程阻塞在 {@code retrieve()}, 虚拟线程循环 {@link #notificationLoop} 直至主线程 {@code interrupt}.
     * </p>
     *
     * @param context         MCP 上下文 (enricher、进度通知用)
     * @param httpRequestSpec 分解后的参数 (path/query/header/body)
     * @param operation       endpoint + path + method
     * @return HTTP 响应; body 类型为 String (JSON 文本)
     * @throws HttpStatusCodeException 下游 4xx/5xx
     * @throws RuntimeException        网络失败, 或进度线程 join 被中断
     */
    private ResponseEntity<String> doExecute(McpRequestContext context, HttpRequestSpec httpRequestSpec, OpenApiOperation operation) {

        // ===================== 阶段 1: Method + URI =====================
        // 无 baseUrl, 每次 call 用 operation.endpoint() 拼完整 URI; lb 与 http 分流出站客户端
        var spec = restClients.forEndpoint(operation.endpoint())
            // OpenAPI HttpMethod → Spring HttpMethod (GET/POST/...)
            .method(HttpMethod.valueOf(operation.method().name()))
            .uri(_ -> {
                // 基址: service_registry.endpoint, 如 lb://g2rain-basis 或 http://10.0.0.1:8080
                var builder = UriComponentsBuilder.fromUriString(operation.endpoint()).path(operation.path());
                // query: MCP arguments 里的 query 参数 → ?key=value
                httpRequestSpec.parameters().query().forEach((name, value) -> addQueryParameter(builder, name, value));
                // path 模板变量: /users/{id} + {id=123} → /users/123
                return builder.build(httpRequestSpec.parameters().path());
            });

        // ===================== 阶段 2: Request Body =====================
        // 仅当 OpenAPI operation 声明了 application/json body 且 LLM 传了 body 时才有值
        httpRequestSpec.resolveRequestBody().ifPresent(body -> {
            spec.body(body.content());
            spec.contentType(body.targetContentType());
        });

        // ===================== 阶段 3: OpenAPI Header 参数 =====================
        // 来自 MCP arguments, 与 enricher 追加的横切 Header 互补 (如 Authorization 若 schema 里有)
        httpRequestSpec.parameters().header().forEach((header, headerValue) -> addHeaderParameter(spec, header, headerValue));

        // ===================== 阶段 4: Enricher 链 + Accept =====================
        // PrincipalContextEnricher / UserAgentEnricher 等按 order 依次追加 Header
        var enrichedSpec = enricherChain.enrich(context, spec);
        // 若 LLM / OpenAPI 未指定 Accept, 默认要 JSON 响应
        enrichedSpec.headers(h -> h.putIfAbsent(HttpHeaders.ACCEPT, List.of(PREFERRED_ACCEPT_MEDIA_TYPE.toString())));

        // ===================== 阶段 5a: 无进度通知 → 直接 HTTP =====================
        Consumer<McpSchema.ProgressNotification> pnc = context.progressNotification();
        McpSchema.CallToolRequest callToolReq = context.callToolRequest();
        if (Objects.isNull(pnc) || Objects.isNull(callToolReq) || Objects.isNull(callToolReq.progressToken())) {
            // 阻塞直到下游响应; 4xx/5xx 在此抛出 HttpStatusCodeException
            return enrichedSpec.retrieve().toEntity(String.class);
        }

        // ===================== 阶段 5b: 有 progressToken → 并行推送进度 =====================
        Double total;
        try {
            // 进度上限 (可为 null, 表示未知总步数)
            total = progressUpdateProvider.total(context);
        } catch (Throwable throwable) {
            // total 算不出来: 降级为不发进度, 主 HTTP 不受影响
            log.warn(
                "Failed to initialise progress notifications because: {} \n No progress notifications will be sent.",
                throwable.getMessage(),
                throwable
            );

            return enrichedSpec.retrieve().toEntity(String.class);
        }

        // 启动虚拟线程: 在 retrieve 阻塞期间周期性 accept ProgressNotification
        var progressNotifThread = Thread.ofVirtual().start(notificationLoop(context, total));

        try {
            // 主线程阻塞: 真正发出 HTTP 并等待响应
            var result = enrichedSpec.retrieve().toEntity(String.class);
            // HTTP 完成 → 通知进度线程退出 (notificationLoop 内 catch InterruptedException)
            progressNotifThread.interrupt();

            try {
                // 等待进度线程结束, 避免虚拟线程泄漏
                progressNotifThread.join();
            } catch (InterruptedException joinInterruptedException) {
                Thread.currentThread().interrupt();
                throw new BusinessException(
                    SystemErrorCode.SYSTEM_INTERNAL_ERROR,
                    null,
                    new Object[]{"Interrupted while waiting for progress notification"},
                    null,
                    joinInterruptedException
                );
            }

            return result;

        } catch (RuntimeException apiCallFail) {
            // retrieve 失败 (网络异常或 HttpStatusCodeException 均属 RuntimeException 体系)
            progressNotifThread.interrupt();

            try {
                progressNotifThread.join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            // 原样抛给 execute, 由 execute 区分 HttpStatusCodeException vs 其它
            throw apiCallFail;
        }
    }

    /**
     * 进度通知循环体, 在独立虚拟线程中运行直至被主线程 {@code interrupt}.
     * <p>
     * 每次 tick: {@link NotificationTicker#tick()} 等待 → {@link ProgressUpdateProvider#next} 取进度
     * → 构造 {@link McpSchema.ProgressNotification} → 通过 context 回调发送给 MCP 客户端.
     * </p>
     *
     * @param ctx   MCP 请求上下文 (含 progressNotification 与 progressToken)
     * @param total 进度总量, 可为 {@code null}
     * @return 供 {@code Thread.ofVirtual().start(...)} 执行的 Runnable 任务
     */
    private Runnable notificationLoop(McpRequestContext ctx, @Nullable Double total) {
        // 闭包捕获: 进度回调与 token (call 期间不变)
        var consumer = Objects.requireNonNull(ctx.progressNotification());
        var progressToken = Objects.requireNonNull(ctx.callToolRequest()).progressToken();
        return () -> {
            var tick = 0L;
            while (true) {
                try {
                    // 等待一个 tick 间隔 (生产环境 1ms, 测试可 mock)
                    ticker.tick();
                    // 按 tick 序号生成当前进度与文案
                    var update = progressUpdateProvider.next(tick++, ctx);
                    McpSchema.ProgressNotification notification = McpSchema.ProgressNotification
                        .builder(progressToken, update.progress())
                        .total(total)
                        .message(update.message())
                        .build();
                    // 通过 MCP SDK 回调推送给客户端
                    consumer.accept(notification);
                } catch (InterruptedException e) {
                    // doExecute 主线程 HTTP 完成后 interrupt → 正常退出循环
                    return;
                } catch (Throwable throwable) {
                    // 单次推送失败: 打 warn 并终止后续 notification, 不影响 HTTP 主流程
                    log.warn(
                        "Failed to send progress notification because: {} \n"
                            + "No further progress notifications will be sent.",
                        throwable.getMessage(),
                        throwable
                    );
                    return;
                }
            }
        };
    }

    /**
     * 向 URI 追加 query 参数.
     * <p>
     * 当前仅支持 OpenAPI 默认 style {@code form} 且 {@code explode=true}:
     * 数组值展开为多个同名 query 参数.
     * </p>
     *
     * @param uriBuilder URI 构建器
     * @param name       参数名
     * @param value      单值或 {@link Iterable} 多值
     */
    private void addQueryParameter(UriBuilder uriBuilder, String name, Object value) {
        // 单值: 直接 append 一个 queryParam
        if (!(value instanceof Iterable<?> items)) {
            uriBuilder.queryParam(name, value.toString());
            return;
        }

        // 数组/集合: explode=true → 同名参数重复出现 (?tag=a&tag=b)
        for (var item : items) {
            if (Objects.isNull(item)) {
                continue;
            }

            uriBuilder.queryParam(name, item.toString());
        }
    }

    /**
     * 向 RestClient 请求追加 header 参数.
     * <p>
     * 当前仅支持 OpenAPI 默认 style {@code simple} 且 {@code explode=false}:
     * 多值以逗号拼接为单个 header 值.
     * </p>
     *
     * @param spec  RestClient 请求规格
     * @param name  header 名
     * @param value 单值或 {@link Iterable} 多值
     */
    private void addHeaderParameter(RestClient.RequestHeadersSpec<?> spec, String name, Object value) {
        // 单值 header: 直接设置
        if (!(value instanceof Iterable<?> items)) {
            spec.header(name, value.toString());
            return;
        }

        // 多值 header: simple style + explode=false → 逗号拼接 (a,b,c)
        var combinedValue = new StringBuilder();
        for (var item : items) {
            if (Objects.isNull(item)) {
                continue;
            }

            if (!combinedValue.isEmpty()) {
                combinedValue.append(",");
            }

            combinedValue.append(item);
        }

        // 过滤后无有效值则跳过, 避免发送空 header
        if (combinedValue.isEmpty()) {
            return;
        }

        spec.header(name, combinedValue.toString());
    }
}
