package com.g2rain.mcp.registry.openapi.naming;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.enums.McpErrorCode;
import com.g2rain.mcp.registry.utils.ToolNamingSupport;

import java.util.Objects;

/**
 * 基于 HTTP 端点信息的 tool code 命名策略.
 * <p>
 * 将 {@link OpenApiOperation} 的 method、contextPath、path 三段信息拼接为一个
 * 小写下划线分隔的 identifier, 作为 MCP tool 的 {@code name} / 数据库 {@code code}.
 * </p>
 * <p>
 * <strong>拼接规则:</strong>
 * </p>
 * <pre>
 * {method}_{sanitized(contextPath)}_{sanitized(path)}
 * </pre>
 * <p>
 * 路径清洗委托 {@link ToolNamingSupport#sanitizePath(String)}, 主要行为:
 * </p>
 * <ul>
 *   <li>{@code /}、{@code -}、{@code .} → {@code _}</li>
 *   <li>{@code {id}} / {@code {id:regex}} → {@code _id} (只取参数名, 忽略正则)</li>
 *   <li>连续 {@code _} 合并为单个</li>
 *   <li>字母统一小写</li>
 * </ul>
 * <p>
 * <strong>示例:</strong>
 * </p>
 * <pre>
 * GET  + /g2rain-basis + /users/{id}       → get_g2rain_basis_users_id
 * POST + (空)         + /api/items/{uuid} → post_api_items_uuid
 * GET  + //user/{id}  + (空)              → get_user_id
 * </pre>
 * <p>
 * 超长时由 {@link ToolNamingSupport#ensureMaxLength(String, String)} 截断并追加 hash 后缀,
 * 保证不超过 varchar(256) 且全局唯一.
 * </p>
 *
 * @author alpha
 * @see NamingStrategy
 * @see ToolNamingSupport
 * @since 2026/6/29
 */
public class EndpointStrategy implements NamingStrategy {

    /**
     * 根据 OpenAPI 操作元数据生成 tool code。
     * <p>
     * 将 method、contextPath、path 三段清洗后拼成小写下划线标识；
     * {@code canonicalKey} 为 {@code method|contextPath|path}，用于超长截断时的 hash 输入，
     * 保证同一端点始终生成相同 suffix。
     * </p>
     *
     * @param operation 含 endpoint、contextPath、path、method、Operation 的完整描述
     * @return 符合 MCP tool name 规范的字符串，长度不超过 varchar(256)
     * @throws BusinessException path 非空但清洗后未贡献任何片段（如 {@code "///"}）
     */
    @Override
    public String name(OpenApiOperation operation) {
        // 1. 提取三段原始输入
        var method = operation.method().name().toLowerCase();
        var contextPath = operation.contextPath();
        var path = operation.path();

        // 2. 构造 hash 输入: 同一端点始终映射到相同 suffix
        var canonicalKey = method + "|" + nullToEmpty(contextPath) + "|" + nullToEmpty(path);

        // 3. 拼接: method 作为前缀, 再依次追加 contextPath / path 清洗片段
        var result = new StringBuilder(method.length() + 64);
        result.append(method);
        ToolNamingSupport.appendSegment(result, contextPath);
        ToolNamingSupport.appendSegment(result, path);

        // 4. 兜底: path 有值但清洗后为空 (如 "///"), 不应静默返回仅含 method 的名称
        if (result.length() == method.length() && Strings.isNotBlank(path)) {
            throw new BusinessException(McpErrorCode.ENDPOINT_NAME_INVALID, path);
        }

        // 5. 超长截断 + hash 后缀, 对齐 varchar(256)
        return ToolNamingSupport.ensureMaxLength(result.toString(), canonicalKey);
    }

    private static String nullToEmpty(String value) {
        // null 不参与 canonicalKey 拼接, 避免 "get|null|/path" 歧义
        return Objects.toString(value, "");
    }
}
