package com.g2rain.mcp.registry.error;


import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.Map;
import java.util.Objects;

/**
 * {@link ErrorModelProvider} 的默认实现, 为常见 HTTP 错误码提供标准化 {@link DefaultErrorModel}.
 * <p>
 * <strong>映射策略 (按优先级):</strong>
 * </p>
 * <ol>
 *   <li>精确匹配 — {@link #DEFAULT_RESPONSE_MAPPING} 中预置的 400/401/403/404/429/502/500 等固定文案</li>
 *   <li>泛化 4xx — 使用 {@link HttpStatus#getReasonPhrase()} + 通用 client error 描述</li>
 *   <li>泛化 5xx — 使用 reason phrase + 通用 server error 描述</li>
 *   <li>兜底 — {@code Unexpected Server Response}</li>
 * </ol>
 * <p>
 * <strong>预留扩展:</strong> {@code request} 与 {@code cause} 当前未参与映射,
 * 后续可按 URI、Header 或异常类型 (如 {@code ConnectException}) 覆盖默认文案.
 * </p>
 *
 * @author alpha
 * @see DefaultErrorModel
 * @see ErrorModelWriter
 * @since 2026/6/30
 */
public class DefaultErrorModelProvider implements ErrorModelProvider<DefaultErrorModel> {

    /**
     * 500 Internal Server Error 的固定模型, 同时作为 {@link #INTERNAL_SERVER_ERROR_JSON_REPRESENTATION} 的数据来源.
     */
    private static final DefaultErrorModel INTERNAL_SERVER_ERROR = new DefaultErrorModel("Internal Server Error", "An unexpected error occurred on the server.");

    /**
     * 常见 HTTP 状态码 → 固定错误模型的只读映射表.
     * <p>
     * key 为 {@link HttpStatus#value()}, value 为预定义的 {@link DefaultErrorModel}.
     * 未收录的状态码走 {@link #provide(HttpStatusCode, HttpServletRequest, Throwable)} 中的泛化分支.
     * </p>
     */
    private static final Map<Integer, DefaultErrorModel> DEFAULT_RESPONSE_MAPPING = Map.of(
        HttpStatus.BAD_REQUEST.value(), new DefaultErrorModel("Bad Request", "Check the request syntax and parameters and try again."),
        HttpStatus.UNAUTHORIZED.value(), new DefaultErrorModel("Unauthorized", "Authentication required. Please provide valid credentials."),
        HttpStatus.FORBIDDEN.value(), new DefaultErrorModel("Forbidden", "Access denied. You don't have permission to access this resource."),
        HttpStatus.NOT_FOUND.value(), new DefaultErrorModel("Not Found", "The requested resource was not found."),
        HttpStatus.TOO_MANY_REQUESTS.value(), new DefaultErrorModel("Too Many Requests", "Request limit exceeded. Please try again later."),
        HttpStatus.BAD_GATEWAY.value(), new DefaultErrorModel("Bad Gateway", "The server received an invalid response from an upstream server."),
        HttpStatus.INTERNAL_SERVER_ERROR.value(), INTERNAL_SERVER_ERROR
    );

    /**
     * 序列化失败时的兜底 JSON 字符串, 字段与 {@link #INTERNAL_SERVER_ERROR} 一致.
     * <p>
     * 供 {@link ErrorModelWriter} 在 {@link com.g2rain.common.json.JsonCodec#obj2str(Object)} 抛异常时使用,
     * 避免「错误处理本身再失败」导致 MCP 客户端收到空内容.
     * </p>
     */
    public static final String INTERNAL_SERVER_ERROR_JSON_REPRESENTATION = "{\"error\":\"%s\",\"description\":\"%s\"}"
        .formatted(INTERNAL_SERVER_ERROR.error(), INTERNAL_SERVER_ERROR.description());

    /**
     * 根据 HTTP 状态码解析 {@link DefaultErrorModel}.
     * <p>
     * 处理流程:
     * </p>
     * <ol>
     *   <li>按状态码数值查 {@link #DEFAULT_RESPONSE_MAPPING}, 命中则直接返回预置模型</li>
     *   <li>未命中且为 4xx → 返回 reason phrase + client error 泛化描述</li>
     *   <li>未命中且为 5xx → 返回 reason phrase + server error 泛化描述</li>
     *   <li>其余 (如 1xx/2xx/3xx 被误传入) → 返回 Unexpected 兜底模型</li>
     * </ol>
     *
     * @param responseStatusCode HTTP 响应状态码
     * @param request            当前请求, 预留扩展, 当前未使用
     * @param cause              原始异常, 预留扩展, 当前未使用
     * @return 非空 {@link DefaultErrorModel}
     */
    @Override
    public @NonNull DefaultErrorModel provide(@NonNull HttpStatusCode responseStatusCode, HttpServletRequest request, Throwable cause) {
        // 优先走预置映射 (400/401/502 等常见码)
        var mappedDefaultModel = DEFAULT_RESPONSE_MAPPING.get(responseStatusCode.value());
        if (Objects.nonNull(mappedDefaultModel)) {
            return mappedDefaultModel;
        }

        // 未预置的状态码: 按 4xx / 5xx 大类泛化
        var httpStatus = HttpStatus.valueOf(responseStatusCode.value());
        if (responseStatusCode.is4xxClientError()) {
            return new DefaultErrorModel(httpStatus.getReasonPhrase(), "A client error occurred. Please check your request.");
        }

        if (responseStatusCode.is5xxServerError()) {
            return new DefaultErrorModel(httpStatus.getReasonPhrase(), "An unexpected server error occurred. Please try again later.");
        }

        // 非标准错误区间 (不应常见), 统一兜底
        return new DefaultErrorModel("Unexpected Server Response", "Please try again.");
    }
}
