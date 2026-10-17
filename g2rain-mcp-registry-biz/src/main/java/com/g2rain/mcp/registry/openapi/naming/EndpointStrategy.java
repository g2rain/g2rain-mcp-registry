package com.g2rain.mcp.registry.openapi.naming;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.enums.McpErrorCode;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.utils.ToolNamingSupport;

import java.util.Locale;
import java.util.Objects;

/**
 * 基于 HTTP 端点信息的 tool code 命名策略.
 * <p>
 * 将 {@link OpenApiOperation} 的 method、serviceSegment、contextPath、path 拼接为一个
 * 小写下划线分隔的 identifier, 作为 MCP tool 的 {@code name}.
 * </p>
 * <p>
 * <strong>拼接规则:</strong>
 * </p>
 * <pre>
 * {method}_{sanitized(serviceSegment)}_{sanitized(contextPath)}_{sanitized(path)}
 * </pre>
 * <p>
 * BUILT_IN 的 {@code serviceSegment} 固定为 {@link #BUILT_IN_SEGMENT}；
 * EXTENSION / CUSTOMER 使用外部服务 {@code code}（禁止使用 {@code self}）。
 * </p>
 * <p>
 * 路径清洗委托 {@link ToolNamingSupport#sanitizePath(String)}.
 * 超长时由 {@link ToolNamingSupport#ensureMaxLength(String, String)} 截断并追加 hash 后缀.
 * </p>
 *
 * @author alpha
 * @see NamingStrategy
 * @see ToolNamingSupport
 * @since 2026/6/29
 */
public class EndpointStrategy implements NamingStrategy {

    /** BUILT_IN 命名中间段；外部服务 code 禁止使用 */
    public static final String BUILT_IN_SEGMENT = "self";

    /**
     * 按资源类型解析命名中间段。
     *
     * @param resourceType {@link ResourceType} 名；空则尽量用 serviceCode
     * @param serviceCode  外部服务编码
     * @return {@code self}、serviceCode 或空串
     */
    public static String resolveServiceSegment(String resourceType, String serviceCode) {
        if (ResourceType.BUILT_IN.name().equals(resourceType)) {
            return BUILT_IN_SEGMENT;
        }
        return Strings.isNotBlank(serviceCode) ? serviceCode.trim() : "";
    }

    /**
     * 根据 OpenAPI 操作元数据生成 tool code。
     *
     * @param operation 含 serviceSegment、contextPath、path、method 的完整描述
     * @return 符合 MCP tool name 规范的字符串，长度不超过 varchar(256)
     * @throws BusinessException path 非空但清洗后未贡献任何片段（如 {@code "///"}）
     */
    @Override
    public String name(OpenApiOperation operation) {
        return build(operation.method().name(), operation.serviceSegment(),
            operation.contextPath(), operation.path());
    }

    /**
     * 直接按四段拼名，避免为命名临时构造假 {@link OpenApiOperation}。
     *
     * @param method         HTTP 方法
     * @param serviceSegment {@code self} 或 serviceCode，可空
     * @param contextPath    上下文路径，可空
     * @param path           HTTP 路径
     */
    public static String build(String method, String serviceSegment, String contextPath, String path) {
        Asserts.isTrue(Strings.isNotBlank(method), SystemErrorCode.PARAM_REQUIRED, "method");
        var http = method.trim().toLowerCase(Locale.ROOT);

        var canonicalKey = http + "|" + nullToEmpty(serviceSegment) + "|"
            + nullToEmpty(contextPath) + "|" + nullToEmpty(path);

        var result = new StringBuilder(http.length() + 64);
        result.append(http);
        ToolNamingSupport.appendSegment(result, serviceSegment);
        ToolNamingSupport.appendSegment(result, contextPath);
        ToolNamingSupport.appendSegment(result, path);

        if (result.length() == http.length() && Strings.isNotBlank(path)) {
            throw new BusinessException(McpErrorCode.ENDPOINT_NAME_INVALID, path);
        }

        return ToolNamingSupport.ensureMaxLength(result.toString(), canonicalKey);
    }

    private static String nullToEmpty(String value) {
        return Objects.toString(value, "");
    }
}
