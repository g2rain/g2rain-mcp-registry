package com.g2rain.mcp.registry.openapi;


import org.jspecify.annotations.Nullable;

/**
 * 转换链对单个接口的产出。
 * <p>
 * 一份 OpenAPI / Swagger 文档里有几个接口，就有几条。{@code problem} 非空表示该接口转换失败，
 * 其余字段可能为空，调用方应跳过写库。
 * </p>
 *
 * @param method           HTTP 方法，大写
 * @param path             接口路径
 * @param name             展示名，优先 summary
 * @param description      接口说明，可空
 * @param toolName         工具名，按 {@link com.g2rain.mcp.registry.openapi.naming.EndpointStrategy} 生成
 * @param inputSchema      MCP inputSchema JSON；失败时为空
 * @param openapiDocument  单接口 OpenAPI 文档 JSON（根级字段 + 单个 path + method，{@code $ref} 已展开）；失败时为空
 * @param problem          该接口的失败原因；成功时为空
 * @author alpha
 * @since 2026/10/16
 */
public record ConvertedOperation(String method, String path, String name, @Nullable String description,
                                 String toolName, @Nullable String inputSchema, @Nullable String openapiDocument,
                                 @Nullable String problem) {
}
