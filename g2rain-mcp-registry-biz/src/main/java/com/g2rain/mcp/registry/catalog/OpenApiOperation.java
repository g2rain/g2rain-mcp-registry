package com.g2rain.mcp.registry.catalog;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

/**
 * 一条可暴露为 MCP Tool 的 OpenAPI 操作快照.
 * <p>
 * 运行时 {@code tools/call} 据此拼出站 URL（{@code endpoint + path}）并分解 LLM 传入的 arguments.
 * </p>
 *
 * <h2>示例</h2>
 * <pre>
 * endpoint  = lb://g2rain-basis
 * path      = /orders/{orderId}
 * method    = GET
 * operation = OpenAPI Operation（含 path 参数 orderId、summary 等）
 * </pre>
 * <p>
 * LLM 传 {@code orderId=abc}，最终请求 {@code GET lb://g2rain-basis/orders/abc}.
 * </p>
 *
 * @param endpoint  调用基址：NATIVE 为服务注册地址（支持 {@code lb://}），外部服务为含上下文路径的完整基址
 * @param path      OpenAPI 操作路径（可含 {@code {param}} 模板）
 * @param method    HTTP 方法
 * @param operation OpenAPI Operation 定义, 供 schema 组合与参数分解
 * @author alpha
 * @since 2026/6/30
 * @see com.g2rain.mcp.registry.schema.InputSchemaComposer
 * @see com.g2rain.mcp.registry.tool.ToolHandler
 */
public record OpenApiOperation(String endpoint, String path, PathItem.HttpMethod method, Operation operation) {
}
