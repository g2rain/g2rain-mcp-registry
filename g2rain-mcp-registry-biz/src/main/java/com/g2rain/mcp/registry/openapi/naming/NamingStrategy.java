package com.g2rain.mcp.registry.openapi.naming;

import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.utils.ToolNamingSupport;

/**
 * MCP Tool {@code code} 命名策略接口.
 * <p>
 * 在 profile 注册阶段, 将 {@link OpenApiOperation} 映射为 MCP {@code tools/list} / {@code tools/call}
 * 使用的 tool name (对应 {@code mcp_tool_profile.code}, varchar(256)).
 * </p>
 * <p>
 * <strong>实现类:</strong>
 * </p>
 * <ul>
 *   <li>{@link EndpointStrategy} — 按 HTTP method + contextPath + path 拼接</li>
 *   <li>{@link OperationIdStrategy} — 按 OpenAPI operationId 转 UPPER_SNAKE_CASE</li>
 * </ul>
 * <p>
 * 具体策略由 Spring 容器注入到 {@link com.g2rain.mcp.registry.tool.ToolRegistry} 使用.
 * </p>
 *
 * @author alpha
 * @see OpenApiOperation
 * @see ToolNamingSupport
 * @since 2026/6/29
 */
public interface NamingStrategy {

    /**
     * 根据 OpenAPI 操作元数据生成 tool code.
     *
     * @param operation 含 endpoint、contextPath、path、method、Operation 的完整描述
     * @return 符合 MCP tool name 规范的字符串, 长度不超过指定范围
     * @throws IllegalArgumentException 输入不足以生成有效名称时 (由具体实现决定)
     */
    String name(OpenApiOperation operation);
}
