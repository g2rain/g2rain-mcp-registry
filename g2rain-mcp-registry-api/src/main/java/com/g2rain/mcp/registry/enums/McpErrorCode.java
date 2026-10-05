package com.g2rain.mcp.registry.enums;


import com.g2rain.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * MCP Registry 模块错误码。
 * <p>
 * 实现 {@link ErrorCode}，供 {@code BusinessException} / {@code Asserts} 使用。
 * code 形如 {@code mcp.4xxxx} / {@code mcp.5xxxx}。
 * </p>
 *
 * @author alpha
 * @since 2025/12/5
 */
@Schema(description = "MCP 模块错误码枚举")
public enum McpErrorCode implements ErrorCode {

    @Schema(description = "无法确定 Tool 名称")
    NAME_CANNOT_BE_DETERMINED("mcp.40001", "Unable to register tool for operation: {0:method} {1:path}. Error determining tool name: {2:message}"),

    @Schema(description = "缺少 tools/call 请求")
    MISSING_CALL_TOOL_REQUEST("mcp.40002", "Missing tools/call request"),

    @Schema(description = "未知 Tool")
    UNKNOWN_TOOL("mcp.40003", "Unknown tool: {0:toolName}"),

    @Schema(description = "operationId 无效，无法命名")
    OPERATION_ID_INVALID("mcp.40004", "Invalid operationId for tool naming: {0:message}"),

    @Schema(description = "无法从 path 推导 Tool 名称")
    ENDPOINT_NAME_INVALID("mcp.40005", "Cannot derive tool name from path: {0:path}"),

    @Schema(description = "JSON Schema 解析失败")
    JSON_SCHEMA_RESOLVE_FAILED("mcp.50001", "Failed to resolve JSON schema for operation: {0:operationId}"),

    @Schema(description = "Tool call Filter 链耗尽")
    TOOL_FILTER_CHAIN_EXHAUSTED("mcp.50002", "Tool call filter chain exhausted without returning a response"),

    @Schema(description = "哈希算法不可用")
    HASH_ALGORITHM_UNAVAILABLE("mcp.50003", "SHA-256 not available"),

    @Schema(description = "MCP Handler 替换失败")
    MCP_HANDLER_PATCH_FAILED("mcp.50004", "Failed to patch MCP tool handlers on bean '{0:beanName}'"),

    @Schema(description = "MCP 反射读取字段失败")
    MCP_REFLECTION_FAILED("mcp.50005", "Failed to read field '{0:fieldName}' from {1:className}"),

    @Schema(description = "不支持的 MCP sessionFactory 类型")
    MCP_SESSION_FACTORY_UNSUPPORTED("mcp.50006", "Unsupported MCP sessionFactory type: {0:type}"),

    @Schema(description = "参数解析器配置错误")
    ARGUMENT_RESOLVER_CONFIG_ERROR("mcp.50007", "{0:message}");

    private final String code;

    private final String messageTemplate;

    /**
     * 构造错误码
     *
     * @param code            错误码（4xxx 客户端错误，5xxx 服务器错误）
     * @param messageTemplate 消息模板（支持 {0:param} 顺序占位符）
     */
    McpErrorCode(String code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String messageTemplate() {
        return messageTemplate;
    }
}
