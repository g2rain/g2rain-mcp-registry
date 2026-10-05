package com.g2rain.mcp.registry.error;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatusCode;

/**
 * HTTP 错误模型提供者, 将状态码与请求上下文映射为结构化错误对象.
 * <p>
 * <strong>职责边界:</strong> 只负责「决定错误内容是什么」, 不负责 JSON 序列化 (由 {@link ErrorModelWriter} 完成).
 * </p>
 * <p>
 * <strong>调用链:</strong>
 * </p>
 * <ol>
 *   <li>{@link com.g2rain.mcp.registry.tool.ToolHandler} 捕获下游调用失败</li>
 *   <li>{@link ErrorModelWriter#writeErrorModelAsJson(HttpStatusCode)} 委托本接口 {@link #provide(HttpStatusCode, HttpServletRequest, Throwable)}</li>
 *   <li>返回模型经 {@link com.g2rain.common.json.JsonCodec#obj2str(Object)} 转为 JSON 字符串</li>
 * </ol>
 * <p>
 * <strong>扩展:</strong> 实现本接口并注册为 Spring Bean, 即可替换 {@link DefaultErrorModelProvider},
 * 例如按 {@code request.getRequestURI()} 或 {@code cause} 类型返回更精确的错误说明.
 * </p>
 *
 * @param <T> 错误模型类型; 默认实现为 {@link DefaultErrorModel}
 * @author alpha
 * @see ErrorModelWriter
 * @see DefaultErrorModelProvider
 * @since 2026/6/30
 */
public interface ErrorModelProvider<T> {

    /**
     * 根据 HTTP 状态码及可选的请求/异常上下文生成错误模型.
     * <p>
     * <strong>参数约定:</strong>
     * </p>
     * <ul>
     *   <li>{@code responseStatusCode} — 必填, 表示本次失败对应的 HTTP 语义 (如 502 Bad Gateway)</li>
     *   <li>{@code request} — MCP tool 出站代理场景通常为 {@code null}; HTTP Filter / Controller 场景可传入当前请求</li>
     *   <li>{@code cause} — 原始异常, 可用于日志或按异常类型定制文案; 可为 {@code null}</li>
     * </ul>
     *
     * @param responseStatusCode HTTP 响应状态码, 不可为 {@code null}
     * @param request            当前 Servlet 请求; 无 Web 上下文时为 {@code null}
     * @param cause              触发错误的异常; 可为 {@code null}
     * @return 非空的错误模型实例, 由 {@link ErrorModelWriter} 负责序列化
     */
    @NonNull T provide(@NonNull HttpStatusCode responseStatusCode, HttpServletRequest request, Throwable cause);
}
