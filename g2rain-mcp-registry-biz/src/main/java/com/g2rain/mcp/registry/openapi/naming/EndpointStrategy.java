package com.g2rain.mcp.registry.openapi.naming;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.enums.McpErrorCode;
import com.g2rain.mcp.registry.utils.ToolNamingSupport;

import java.util.Locale;

/**
 * MCP 工具名生成规则。
 * <p>
 * 拼接规则：
 * </p>
 * <pre>
 * {sourcePrefix}_{serviceCode}_{httpMethod}_{normalizedPath}
 * </pre>
 * <ul>
 *   <li>{@code sourcePrefix}：资源类型小写（{@code native}、{@code extension}、{@code customer}）</li>
 *   <li>{@code serviceCode}：OpenAPI {@code info.title} 规范化，见 {@link #serviceCode(String)}</li>
 *   <li>{@code httpMethod}：小写 HTTP 方法</li>
 *   <li>{@code normalizedPath}：OpenAPI path 规范化，见 {@link ToolNamingSupport#sanitizePath(String)}</li>
 * </ul>
 * <p>
 * 例：{@code NATIVE} + {@code g2rain-basis} + {@code GET} + {@code /user/identities}
 * → {@code native_g2rain_basis_get_user_identities}。超长时截断并拼接哈希，见
 * {@link ToolNamingSupport#ensureMaxLength(String, String)}。
 * </p>
 *
 * @author alpha
 * @since 2026/6/29
 */
public final class EndpointStrategy {

    private EndpointStrategy() {
    }

    /**
     * 由 OpenAPI {@code info.title} 得到服务编码。
     *
     * @param title 文档标题
     * @return 规范化后的服务编码，如 {@code g2rain-basis} → {@code g2rain_basis}
     * @throws BusinessException 规范化后为空（如纯中文标题）
     */
    public static String serviceCode(String title) {
        String code = ToolNamingSupport.sanitize(title);
        if (code.isEmpty()) {
            throw new BusinessException(McpErrorCode.SERVICE_TITLE_INVALID, Strings.isBlank(title) ? "" : title);
        }
        return code;
    }

    /**
     * 生成工具名。
     *
     * @param resourceType 资源类型名
     * @param serviceCode  服务编码（已规范化）
     * @param method       HTTP 方法
     * @param path         OpenAPI path
     * @return 工具名，长度不超过 64
     * @throws BusinessException path 清洗后为空
     */
    public static String build(String resourceType, String serviceCode, String method, String path) {
        Asserts.isTrue(Strings.isNotBlank(resourceType), SystemErrorCode.PARAM_REQUIRED, "resourceType");
        Asserts.isTrue(Strings.isNotBlank(serviceCode), SystemErrorCode.PARAM_REQUIRED, "serviceCode");
        Asserts.isTrue(Strings.isNotBlank(method), SystemErrorCode.PARAM_REQUIRED, "method");

        String normalizedPath = ToolNamingSupport.sanitizePath(path);
        if (normalizedPath.isEmpty()) {
            throw new BusinessException(McpErrorCode.ENDPOINT_NAME_INVALID, Strings.isBlank(path) ? "" : path);
        }

        String prefix = resourceType.trim().toLowerCase(Locale.ROOT);
        String httpMethod = method.trim().toLowerCase(Locale.ROOT);
        String name = String.join("_", prefix, serviceCode, httpMethod, normalizedPath);
        String uniquenessKey = String.join("|", prefix, serviceCode, httpMethod, path);
        return ToolNamingSupport.ensureMaxLength(name, uniquenessKey);
    }
}
