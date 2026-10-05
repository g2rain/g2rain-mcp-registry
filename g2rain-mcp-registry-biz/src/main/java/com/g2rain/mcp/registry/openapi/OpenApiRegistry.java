package com.g2rain.mcp.registry.openapi;


/**
 * OpenAPI 文档的集中注册与查询入口（预留）.
 * <p>
 * g2rain MCP Registry 的运行时 Tool 来自 {@link com.g2rain.mcp.registry.tool.ToolRegistry}
 * 内存缓存; 本类计划承载「从 basis / 其它服务拉取 OpenAPI 文档、解析为
 * {@link com.g2rain.mcp.registry.catalog.OpenApiOperation}」的能力,
 * 供 profile 保存或热更新时调用.
 * </p>
 * <p>
 * 当前为空实现, 不影响 {@code tools/list} 与 {@code tools/call} 主链路;
 * Tool 注册数据暂由外部流程构造 {@link com.g2rain.mcp.registry.catalog.OpenApiOperation} 后直接
 * 调用 {@link com.g2rain.mcp.registry.tool.ToolRegistry#register}.
 * </p>
 *
 * @author alpha
 * @since 2026/6/30
 * @see com.g2rain.mcp.registry.catalog.OpenApiOperation
 * @see com.g2rain.mcp.registry.tool.ToolRegistry
 */
public class OpenApiRegistry {
}
