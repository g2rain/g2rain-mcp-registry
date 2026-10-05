package com.g2rain.mcp.registry.web;


import com.g2rain.common.json.JsonCodec;
import com.g2rain.common.json.JsonCodecFactory;
import com.g2rain.common.utils.Strings;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * <p>{@code McpJsonRpcResponses} 是 MCP HTTP 传输层的 JSON-RPC 错误响应构造工具类。</p>
 *
 * <p><b>职责说明：</b></p>
 * <ul>
 *     <li>将业务/系统异常转换为符合 MCP 规范的 {@link McpSchema.JSONRPCResponse} 字节序列。</li>
 *     <li>从原始请求体中解析 {@code id} 字段，保证错误响应与请求一一对应。</li>
 *     <li>在请求体缺失或不可解析时，使用固定回退 id，避免客户端无法关联请求。</li>
 * </ul>
 *
 * <p><b>使用场景：</b></p>
 * <ul>
 *     <li>{@link McpGlobalExceptionFilter} 捕获 Filter 链异常后写入 JSON-RPC error。</li>
 *     <li>{@link McpHandlerFilter} 在未认证时返回 {@code 401} 及 JSON-RPC error 体。</li>
 * </ul>
 *
 * <p>本类为包内可见的工具类，禁止实例化。</p>
 *
 * @see McpSchema.JSONRPCResponse
 * @see McpSchema.JSONRPCResponse.JSONRPCError
 */
final class McpJsonRpcResponses {

    /**
     * 全局 JSON 编解码器，用于序列化 {@link McpSchema.JSONRPCResponse} 及反序列化请求体。
     * <p>通过 {@link JsonCodecFactory#instance()} 获取单例，保证与项目其他模块序列化行为一致。</p>
     */
    private static final JsonCodec JSON_CODEC = JsonCodecFactory.instance();

    /**
     * 无法从请求体解析 {@code id} 时使用的回退值。
     * <p>JSON-RPC 2.0 允许 {@code id} 为字符串、数字或 {@code null}；此处使用整数 {@code 0}
     * 作为占位符，便于客户端识别为"无法关联原始请求"的兜底响应。</p>
     */
    private static final Object FALLBACK_REQUEST_ID = 0;

    /**
     * 私有构造器，禁止实例化工具类。
     */
    private McpJsonRpcResponses() {
    }

    /**
     * 构造 JSON-RPC 错误响应的字节数组，可直接写入 HTTP 响应体。
     *
     * <p><b>处理流程：</b></p>
     * <ol>
     *     <li>从 {@code requestBody} 解析 JSON-RPC {@code id}（见 {@link #resolveRequestId(String)}）。</li>
     *     <li>构造 {@link McpSchema.JSONRPCResponse.JSONRPCError}，包含错误码与消息。</li>
     *     <li>组装完整的 {@link McpSchema.JSONRPCResponse#error(Object, McpSchema.JSONRPCResponse.JSONRPCError)}。</li>
     *     <li>序列化为 UTF-8 JSON 字节数组。</li>
     * </ol>
     *
     * @param errorCode   JSON-RPC 错误码，通常取自 {@link McpSchema.ErrorCodes}
     * @param message     面向客户端的错误描述信息
     * @param requestBody 原始 HTTP 请求体字符串，用于提取 {@code id}；可为 {@code null} 或空
     * @return JSON-RPC error 响应的 UTF-8 字节数组，Content-Type 应为 {@code application/json}
     */
    static byte[] errorBytes(int errorCode, String message, String requestBody) {
        // 从请求体提取 id，保证 error 响应与 request 配对
        Object requestId = resolveRequestId(requestBody);
        // 构造 JSON-RPC 标准 error 对象，data 字段暂不使用
        var jsonRpcError = new McpSchema.JSONRPCResponse.JSONRPCError(errorCode, message, null);
        // 组装完整 JSON-RPC 响应：{ "jsonrpc": "2.0", "id": ..., "error": ... }
        var response = McpSchema.JSONRPCResponse.error(requestId, jsonRpcError);
        // 序列化为字节数组，供 Filter 直接写入 OutputStream
        return JSON_CODEC.obj2byte(response);
    }

    /**
     * 从 JSON-RPC 请求体字符串中解析 {@code id} 字段。
     *
     * <p><b>解析规则：</b></p>
     * <ul>
     *     <li>请求体为空或仅空白 → 返回 {@link #FALLBACK_REQUEST_ID}。</li>
     *     <li>{@code id} 缺失或为 JSON {@code null} → 返回 {@link #FALLBACK_REQUEST_ID}。</li>
     *     <li>{@code id} 为数字 → 在 {@code int} 范围内返回 {@code Integer}，否则返回 {@code Long}。</li>
     *     <li>{@code id} 为字符串 → 原样返回字符串值。</li>
     *     <li>JSON 解析失败或发生其他异常 → 静默回退 {@link #FALLBACK_REQUEST_ID}。</li>
     * </ul>
     *
     * @param requestBody 原始请求体；可为 {@code null}
     * @return JSON-RPC 请求 id，类型为 {@code Integer}、{@code Long} 或 {@code String}；解析失败时返回 {@link #FALLBACK_REQUEST_ID}
     */
    private static Object resolveRequestId(String requestBody) {
        // 无请求体时无法提取 id，直接使用回退值
        if (Strings.isBlank(requestBody)) {
            return FALLBACK_REQUEST_ID;
        }

        try {
            // 将请求体解析为 JsonNode 树结构
            JsonNode root = JSON_CODEC.str2obj(requestBody, JsonNode.class);
            // 读取顶层 "id" 字段
            JsonNode idNode = root.get("id");
            // id 不存在或为 JSON null 时回退
            if (Objects.isNull(idNode) || idNode.isNull()) {
                return FALLBACK_REQUEST_ID;
            }
            // 数字类型 id：优先转为 int，超出范围则保留 long
            if (idNode.isNumber()) {
                long value = idNode.longValue();
                if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                    return (int) value;
                }
                return value;
            }
            // 字符串类型 id：按原值返回
            if (idNode.isString()) {
                return idNode.asString();
            }
        } catch (Exception ignored) {
            // 请求体不可解析（非 JSON、格式错误等）时回退固定 id，不影响错误响应下发
        }

        // 其他类型（如布尔、对象、数组）的 id 不支持，回退固定值
        return FALLBACK_REQUEST_ID;
    }
}
