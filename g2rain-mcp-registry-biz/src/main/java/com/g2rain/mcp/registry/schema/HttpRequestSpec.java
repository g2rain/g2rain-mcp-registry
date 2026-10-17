package com.g2rain.mcp.registry.schema;


import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 结构化 HTTP 请求模型, 由 {@link InputSchemaComposer#decompose} 产出,
 * 供 {@link com.g2rain.mcp.registry.tool.ToolHandler#execute} 消费.
 * <p>
 * 连接 MCP {@code tools/call} 参数与实际出站 HTTP 调用: parameters 按 OpenAPI {@code in}
 * 位置分组 (path / query / header / cookie), 可选的 requestBody 携带 JSON 载荷及 Content-Type 元数据.
 * </p>
 *
 * <h2>生命周期</h2>
 * <ol>
 *   <li>{@link InputSchemaComposer#compose} 从 OpenAPI 生成 MCP Tool input schema.</li>
 *   <li>{@link InputSchemaComposer#decompose} 将调用参数解析为 {@code HttpRequestSpec}.</li>
 *   <li>{@code ToolHandler} 据此组装并发送 HTTP 请求.</li>
 * </ol>
 *
 * <h2>支持范围</h2>
 * <ul>
 *   <li>Parameters: path, query, header, cookie (见 {@link Parameters}).</li>
 *   <li>RequestBody: 仅 {@link #SUPPORTED_MEDIA_TYPE} ({@code application/json}).</li>
 * </ul>
 *
 * @param parameters  按位置分组的 path / query / header / cookie 参数值
 * @param requestBody JSON 请求体及 media type 元数据; 无 body 时为 {@code null}
 * @author alpha
 * @see InputSchemaComposer
 * @see Parameters
 * @see Body
 * @since 2026/6/29
 */
public record HttpRequestSpec(Parameters parameters, @Nullable Body requestBody) {

    /**
     * g2rain-mcp-registry 唯一支持的 requestBody media type.
     * <p>
     * 用于 {@link InputSchemaComposer} 解析 OpenAPI {@code requestBody.content},
     * 以及 {@link Body#inDefaultSupportedMediaType(Object)} 包装分解后的 body 内容.
     * </p>
     */
    public static final MediaType SUPPORTED_MEDIA_TYPE = MediaType.APPLICATION_JSON;

    /**
     * g2rain-mcp-registry 支持的 OpenAPI parameter {@code in} 位置.
     */
    static final Set<String> SUPPORTED_PARAMETER_TYPES = Set.of(
        Parameters.QUERY,
        Parameters.PATH,
        Parameters.HEADER,
        Parameters.COOKIE
    );

    /**
     * 返回空模型: 无 parameters, 无 requestBody.
     * <p>
     * 用于分解失败或 {@code CallToolRequest} 为 {@code null} 的场景.
     * </p>
     *
     * @return 空的 {@code HttpRequestSpec}
     */
    public static HttpRequestSpec empty() {
        return new HttpRequestSpec(Parameters.empty(), null);
    }

    /**
     * 返回仅含 parameters、无 requestBody 的模型.
     * <p>
     * 适用于 Operation 只声明 parameters、无 JSON body 的场景
     * (如带 path/query 参数的 {@code GET} 请求).
     * </p>
     *
     * @param parameters 按 path / query / header / cookie 分组的参数值
     * @return 仅 parameters 的 {@code HttpRequestSpec}
     */
    public static HttpRequestSpec withParameters(Parameters parameters) {
        return new HttpRequestSpec(parameters, null);
    }

    /**
     * 返回同时含 parameters 与可选 JSON requestBody 的模型.
     * <p>
     * 适用于 Operation 同时声明 parameters 与 {@code application/json} requestBody.
     * 若 {@code content} 为 {@code null}, 等价于 {@link #withParameters(Parameters)}.
     * </p>
     *
     * @param parameters 按 path / query / header / cookie 分组的参数值
     * @param content    MCP arguments 中的 body 原始内容; {@code null} 表示无 body
     * @return 组合型 {@code HttpRequestSpec}
     */
    public static HttpRequestSpec withParametersAndBodyContent(Parameters parameters, @Nullable Object content) {
        if (Objects.isNull(content)) {
            return HttpRequestSpec.withParameters(parameters);
        }

        return new HttpRequestSpec(parameters, Body.inDefaultSupportedMediaType(content));
    }

    /**
     * 返回仅含 JSON requestBody、无 parameters 的模型.
     * <p>
     * 适用于 Operation 只声明 {@code application/json} requestBody 的场景
     * (如纯 JSON {@code POST} 请求).
     * </p>
     *
     * @param content MCP arguments 中的 body 原始内容
     * @return 仅 body 的 {@code HttpRequestSpec}
     */
    public static HttpRequestSpec withRequestBody(Object content) {
        return new HttpRequestSpec(Parameters.empty(), Body.inDefaultSupportedMediaType(content));
    }

    /**
     * 按 OpenAPI parameter {@code in} 位置分组的 HTTP 参数.
     * <p>
     * 每个 Map 的 key 为 OpenAPI parameter name, value 为调用方传入的实际值.
     * 由 {@link InputSchemaComposer} 在 {@code decompose} 阶段填充.
     * </p>
     *
     * @param path   path 参数 (如 {@code orderId -> "abc-123"})
     * @param query  query 参数 (如 {@code page -> 1})
     * @param header header 参数 (如 {@code X-Trace-Id -> "trace-001"})
     * @param cookie cookie 参数 (如 {@code sessionToken -> "abc"})
     */
    public record Parameters(Map<String, Object> path, Map<String, Object> query, Map<String, Object> header,
                             Map<String, Object> cookie) {

        /**
         * OpenAPI parameter 位置常量: {@code in: path}.
         */
        public static final String PATH = "path";

        /**
         * OpenAPI parameter 位置常量: {@code in: query}.
         */
        public static final String QUERY = "query";

        /**
         * OpenAPI parameter 位置常量: {@code in: header}.
         */
        public static final String HEADER = "header";

        /**
         * OpenAPI parameter 位置常量: {@code in: cookie}（OpenAPI 3.x）.
         */
        public static final String COOKIE = "cookie";

        /**
         * 返回空的 parameters 容器, 不含任何 path / query / header / cookie 条目.
         *
         * @return 空的 {@code Parameters}
         */
        public static Parameters empty() {
            return new Parameters(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    /**
     * 带 Content-Type 元数据的 HTTP 请求体.
     * <p>
     * 包装 MCP arguments 反序列化后的 JSON 内容及目标 {@link MediaType},
     * 供下游 HTTP 客户端设置正确的 {@code Content-Type} 请求头.
     * </p>
     *
     * @param targetContentType 序列化使用的 media type (通常为 {@link #SUPPORTED_MEDIA_TYPE})
     * @param content           反序列化后的 body 载荷 (通常为 {@code Map} 或类似结构)
     */
    public record Body(MediaType targetContentType, Object content) {

        /**
         * 使用 {@link HttpRequestSpec#SUPPORTED_MEDIA_TYPE} ({@code application/json}) 创建 body.
         *
         * @param content MCP arguments 中的 JSON body 原始内容
         * @return 标记为默认支持 media type 的 {@code Body}
         */
        public static Body inDefaultSupportedMediaType(Object content) {
            return new Body(SUPPORTED_MEDIA_TYPE, content);
        }
    }

    /**
     * 以 {@link Optional} 形式返回 requestBody, 无 body 时为空.
     * <p>
     * 供下游代码用 Optional 链式调用, 避免直接 null 判断.
     * </p>
     *
     * @return {@link #requestBody} 非 null 时 {@link Optional#of(Object)},
     * 否则 {@link Optional#empty()}
     */
    public Optional<Body> resolveRequestBody() {
        return Optional.ofNullable(requestBody);
    }
}
