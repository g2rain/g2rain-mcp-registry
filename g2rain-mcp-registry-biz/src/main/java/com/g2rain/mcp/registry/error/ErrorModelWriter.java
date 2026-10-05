package com.g2rain.mcp.registry.error;

import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecFactory;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatusCode;

/**
 * 将 {@link ErrorModelProvider} 产出的错误模型序列化为 JSON 字符串.
 * <p>
 * <strong>用途:</strong> {@link com.g2rain.mcp.registry.tool.ToolHandler} 在下游 HTTP 调用失败
 * (网络不可达、连接超时、502 等 {@link RuntimeException}) 时, 构造 MCP
 * {@link io.modelcontextprotocol.spec.McpSchema.CallToolResult} 的文本内容, 使 LLM 能读到结构化错误信息.
 * </p>
 * <p>
 * <strong>与 {@link org.springframework.web.client.HttpStatusCodeException} 的分工:</strong>
 * ToolHandler 对 4xx/5xx 响应体通常直接透传下游 JSON; 本类主要用于「根本没拿到 HTTP 响应」
 * 或需要平台统一错误格式的场景.
 * </p>
 * <p>
 * <strong>序列化:</strong> 使用 {@link JsonCodecFactory#instance()} 提供的平台默认 {@link JsonCodec};
 * 序列化失败时回退 {@link DefaultErrorModelProvider#INTERNAL_SERVER_ERROR_JSON_REPRESENTATION}.
 * </p>
 *
 * @author alpha
 * @see ErrorModelProvider
 * @see DefaultErrorModelProvider
 * @see com.g2rain.mcp.registry.tool.ToolHandler
 * @since 2026/6/30
 */
@Slf4j
@Component
public class ErrorModelWriter {

    /**
     * 平台默认 JSON 编解码器, 通过 {@link JsonCodecFactory#instance()} 获取单例, 避免在本类重复配置 {@code JsonMapper}.
     */
    private static final JsonCodec jsonCodec = JsonCodecFactory.instance();

    /**
     * 错误模型提供者, 决定「错误 JSON 里写什么字段、什么文案」.
     * <p>
     * 构造器注入, 默认实现为 {@link DefaultErrorModelProvider}, 可按环境替换.
     * </p>
     */
    private final ErrorModelProvider<?> errorModelProvider;

    /**
     * @param errorModelProvider 错误模型提供者, 通常注入 {@link DefaultErrorModelProvider}
     */
    public ErrorModelWriter(ErrorModelProvider<?> errorModelProvider) {
        this.errorModelProvider = errorModelProvider;
    }

    /**
     * 根据状态码及完整 Web 上下文生成错误 JSON.
     * <p>
     * 处理流程:
     * </p>
     * <ol>
     *   <li>调用 {@link ErrorModelProvider#provide(HttpStatusCode, HttpServletRequest, Throwable)} 得到错误模型</li>
     *   <li>使用 {@link JsonCodec#obj2str(Object)} 序列化为 JSON 字符串</li>
     *   <li>序列化异常 → 记录 error 日志, 返回 {@link DefaultErrorModelProvider#INTERNAL_SERVER_ERROR_JSON_REPRESENTATION}</li>
     * </ol>
     *
     * @param statusCode HTTP 状态码, 不可为 {@code null}
     * @param request    当前 Servlet 请求, 可为 {@code null} (MCP tool 出站场景)
     * @param cause      原始异常, 可为 {@code null}, 供 Provider 扩展使用
     * @return JSON 字符串, 永不为 {@code null}
     */
    public @NonNull String writeErrorModelAsJson(@NonNull HttpStatusCode statusCode, HttpServletRequest request, Throwable cause) {
        // 1. 由 Provider 按状态码 (及可选 request/cause) 构造错误模型
        var errorModel = errorModelProvider.provide(statusCode, request, cause);
        try {
            // 2. 序列化为 JSON, 供 CallToolResult.addTextContent 使用
            return jsonCodec.obj2str(errorModel);
        } catch (Exception e) {
            // 3. 序列化本身失败: 使用预格式化的 500 JSON, 保证调用方始终有内容可返回
            log.error("Error serializing error model. The default error response will be used.", e);
            return DefaultErrorModelProvider.INTERNAL_SERVER_ERROR_JSON_REPRESENTATION;
        }
    }

    /**
     * 仅根据状态码生成错误 JSON, 无 Servlet 请求上下文时的便捷重载.
     * <p>
     * <strong>典型调用方:</strong> {@link com.g2rain.mcp.registry.tool.ToolHandler} 在
     * {@code catch (RuntimeException)} 分支中调用 {@code writeErrorModelAsJson(HttpStatus.BAD_GATEWAY)},
     * 此时无 inbound {@link HttpServletRequest}, {@code request} 与 {@code cause} 均传 {@code null}.
     * </p>
     *
     * @param statusCode HTTP 状态码
     * @return JSON 字符串, 永不为 {@code null}
     */
    public @NonNull String writeErrorModelAsJson(@NonNull HttpStatusCode statusCode) {
        return writeErrorModelAsJson(statusCode, null, null);
    }
}
