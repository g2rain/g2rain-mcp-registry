package com.g2rain.mcp.registry.error;

/**
 * 默认 HTTP 错误响应体模型.
 * <p>
 * <strong>用途:</strong> 作为 MCP Registry 在 tool 调用失败或 HTTP 层出错时, 返回给 MCP 客户端的标准 JSON 结构.
 * 由 {@link DefaultErrorModelProvider} 根据 {@link org.springframework.http.HttpStatusCode} 实例化,
 * 再经 {@link ErrorModelWriter} 序列化为字符串, 填入 {@link io.modelcontextprotocol.spec.McpSchema.CallToolResult}.
 * </p>
 * <p>
 * <strong>JSON 形态示例:</strong>
 * </p>
 * <pre>{@code
 * {
 *   "error": "Bad Gateway",
 *   "description": "The server received an invalid response from an upstream server."
 * }
 * }</pre>
 * <p>
 * <strong>扩展:</strong> 若需接入 g2rain 平台统一 {@code Result} 结构, 可新增 record 并实现 {@link ErrorModelProvider},
 * 无需修改 {@link ErrorModelWriter} 的序列化逻辑 (依赖 {@link com.g2rain.common.json.JsonCodec} 泛型序列化).
 * </p>
 *
 * @param error       错误标题, 通常对应 HTTP reason phrase (如 {@code Bad Gateway})
 * @param description 面向调用方 (LLM / MCP 客户端) 的可读说明, 便于理解失败原因
 * @author alpha
 * @see DefaultErrorModelProvider
 * @see ErrorModelWriter
 * @since 2026/6/30
 */
public record DefaultErrorModel(String error, String description) {

}
