package com.g2rain.mcp.registry.catalog;


import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

/**
 * 一条可暴露为 MCP Tool 的 OpenAPI 操作快照.
 * <p>
 * 在 Tool 注册阶段, {@link com.g2rain.mcp.registry.tool.ToolRegistry} 用本 record 描述
 * 「要调哪个下游服务、走哪条路径、用什么 HTTP 方法、OpenAPI 里怎么定义入参」.
 * 运行时 {@code tools/call} 会据此拼出站 URL 并解析 LLM 传入的 arguments.
 * </p>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>{@code endpoint} — 下游服务基址, 如 {@code lb://g2rain-basis} 或 {@code http://10.0.0.8:8080}</li>
 *   <li>{@code contextPath} — 服务上下文路径, 如 {@code /g2rain-basis}, 可为空</li>
 *   <li>{@code path} — 操作路径, 如 {@code /orders/{orderId}}</li>
 *   <li>{@code method} — HTTP 方法, 如 {@code GET}、{@code POST}</li>
 *   <li>{@code operation} — OpenAPI {@link Operation} 对象, 含 parameters、requestBody、summary 等</li>
 *   <li>{@code serviceSegment} — 命名中间段：BUILT_IN 为 {@code self}，外部服务为 serviceCode，可空</li>
 * </ul>
 *
 * <h2>示例</h2>
 * <p>
 * 假设 basis 服务有一个「按 ID 查订单」接口:
 * </p>
 * <pre>
 * endpoint       = lb://g2rain-basis
 * contextPath    = /g2rain-basis
 * path           = /orders/{orderId}
 * method         = GET
 * serviceSegment = self
 * operation      = OpenAPI Operation（含 path 参数 orderId、summary 等）
 * </pre>
 * <p>
 * 注册后可能生成 tool code {@code get_self_g2rain_basis_orders_order_id},
 * {@code tools/call} 时 LLM 传 {@code orderId=abc}, 最终请求
 * {@code GET lb://g2rain-basis/g2rain-basis/orders/abc}.
 * </p>
 *
 * @param endpoint       下游服务基址（支持 {@code lb://} 与 {@code http://}）
 * @param contextPath    服务上下文路径, 参与 tool 命名与 URL 拼接
 * @param path           OpenAPI 操作路径（可含 {@code {param}} 模板）
 * @param method         HTTP 方法
 * @param operation      OpenAPI Operation 定义, 供 schema 组合与参数分解
 * @param serviceSegment 命名中间段（{@code self} 或 serviceCode）
 * @author alpha
 * @since 2026/6/30
 * @see com.g2rain.mcp.registry.schema.InputSchemaComposer
 * @see com.g2rain.mcp.registry.tool.ToolHandler
 */
public record OpenApiOperation(String endpoint, String contextPath, String path, PathItem.HttpMethod method,
                               Operation operation, String serviceSegment) {

}
