package com.g2rain.mcp.registry.openapi.naming;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.enums.McpErrorCode;
import com.g2rain.mcp.registry.utils.ToolNamingSupport;

import java.util.Objects;

/**
 * 基于 OpenAPI operationId 的 tool code 命名策略.
 * <p>
 * 直接读取 {@link io.swagger.v3.oas.models.Operation#getOperationId()}, 转换为
 * {@code UPPER_SNAKE_CASE} 格式作为 MCP tool name.
 * </p>
 * <p>
 * <strong>转换规则:</strong>
 * </p>
 * <ul>
 *   <li>字母数字保留, 统一大写</li>
 *   <li>camelCase 边界插入 {@code _} (如 {@code getUserById} → {@code GET_USER_BY_ID})</li>
 *   <li>非字母数字字符 (空格、连字符等) → {@code _}</li>
 *   <li>连续 {@code _} 合并, 去除首尾 {@code _}</li>
 * </ul>
 * <p>
 * <strong>示例:</strong>
 * </p>
 * <pre>
 * getUserById      → GET_USER_BY_ID
 * send-sms-message → SEND_SMS_MESSAGE
 * createOrder      → CREATE_ORDER
 * </pre>
 * <p>
 * 超长时同样委托 {@link ToolNamingSupport#ensureMaxLength(String, String)} 处理.
 * </p>
 *
 * @author alpha
 * @see NamingStrategy
 * @see EndpointStrategy
 * @since 2026/6/29
 */
public class OperationIdStrategy implements NamingStrategy {

    /**
     * 根据 OpenAPI 操作元数据生成 tool code。
     * <p>
     * 读取 {@code operationId}，按 camelCase / 分隔符规则转为 {@code UPPER_SNAKE_CASE}；
     * 超长时用原始 operationId 作 hash 输入，保证同一 id 后缀稳定。
     * </p>
     *
     * @param operation 含 endpoint、contextPath、path、method、Operation 的完整描述
     * @return 符合 MCP tool name 规范的字符串，长度不超过指定范围
     * @throws BusinessException operationId 为 null、空白或不含任何字母数字
     */
    @Override
    public String name(OpenApiOperation operation) {
        var operationId = operation.operation().getOperationId();

        if (Objects.isNull(operationId)) {
            throw new BusinessException(
                McpErrorCode.OPERATION_ID_INVALID,
                "Operation ID is null - cannot determine how to proceed with naming."
            );
        }

        var trimmed = operationId.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(
                McpErrorCode.OPERATION_ID_INVALID,
                "Operation ID is empty - cannot determine how to proceed with naming."
            );
        }

        // 逐字符扫描, 转为 UPPER_SNAKE_CASE
        var result = new StringBuilder(trimmed.length() + 8);
        var lastWasUnderscore = false;

        for (int i = 0, j = trimmed.length(); i < j; i++) {
            char c = trimmed.charAt(i);

            if (Character.isLetterOrDigit(c)) {
                // camelCase 大写边界前插 _ (getUserById → GET_USER_BY_ID)
                if (Character.isUpperCase(c) && !result.isEmpty() && !lastWasUnderscore) {
                    result.append('_');
                }
                result.append(Character.toUpperCase(c));
                lastWasUnderscore = false;
                continue;
            }

            // 非字母数字 (空格、连字符等) → 单个 _
            if (!lastWasUnderscore && !result.isEmpty()) {
                result.append('_');
                lastWasUnderscore = true;
            }
        }

        // 去掉尾部 _
        if (!result.isEmpty() && result.charAt(result.length() - 1) == '_') {
            result.setLength(result.length() - 1);
        }

        // 全是符号无字母数字, 无法生成有效 code
        if (result.isEmpty()) {
            throw new BusinessException(
                McpErrorCode.OPERATION_ID_INVALID,
                String.format(
                    "Operation ID '%s' contains no valid alphanumeric characters - cannot generate a valid name.",
                    operationId
                )
            );
        }

        // 超长时用原始 operationId 作为 hash 输入, 保证同一 operationId 后缀稳定
        return ToolNamingSupport.ensureMaxLength(result.toString(), trimmed);
    }
}
