package com.g2rain.mcp.registry.utils;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.enums.McpErrorCode;
import com.g2rain.mcp.registry.openapi.naming.EndpointStrategy;
import com.g2rain.mcp.registry.openapi.naming.OperationIdStrategy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * MCP tool code 命名的公共工具类.
 * <p>
 * 供 {@link EndpointStrategy} 和 {@link OperationIdStrategy} 共用, 封装三类能力:
 * </p>
 * <ol>
 *   <li><strong>路径清洗</strong> — {@link #sanitizePath(String)} / {@link #appendSegment(StringBuilder, String)}</li>
 *   <li><strong>长度限制</strong> — {@link #ensureMaxLength(String, String)}, 对齐 DB {@code varchar(256)}</li>
 *   <li><strong>唯一性保证</strong> — 超长截断时追加 SHA-256 前 8 位 hex, 避免碰撞</li>
 * </ol>
 * <p>
 * <strong>路径参数正则 {@link #PATH_PARAM}:</strong>
 * {@code \{([^}:]+)(?::[^}]*)?\}} — 匹配 OpenAPI 标准写法 {@code {name}} 或 {@code {name:regex}},
 * 只捕获冒号前的参数名.
 * </p>
 * <p>
 * 本类仅在 profile 注册阶段调用, 不在 {@code tools/list} / {@code tools/call} 热路径上.
 * </p>
 *
 * @author alpha
 * @since 2026/6/29
 */
public final class ToolNamingSupport {

    /**
     * tool code 最大长度, 与 {@code mcp_tool_profile.code} 字段 varchar(256) 对齐.
     */
    private static final int MAX_TOOL_NAME_LENGTH = 256;

    /**
     * 超长截断时 hash 后缀的 hex 字符数.
     */
    private static final int HASH_SUFFIX_LENGTH = 8;

    /**
     * OpenAPI 路径模板匹配: {@code {param}} 或 {@code {param:pattern}}.
     */
    private static final Pattern PATH_PARAM = Pattern.compile("\\{([^}:]+)(?::[^}]*)?}");

    private ToolNamingSupport() {

    }

    /**
     * 将 URL 路径段清洗为小写下划线 identifier.
     * <p>
     * 处理顺序: 先替换路径参数模板, 再逐字符转换分隔符.
     * 开头连续的 {@code /} 不产生前导 {@code _}; 中间连续分隔符合并为单个 {@code _}.
     * </p>
     *
     * @param path 原始路径, 如 {@code /users/{id}} 或 {@code //user/{id}}; blank 时返回空串
     * @return 清洗后的片段, 如 {@code users_id}; 无可保留字符时返回空串
     */
    public static String sanitizePath(String path) {
        if (Strings.isBlank(path)) {
            return "";
        }

        // 阶段 1: {id} / {id:regex} → _id, 只保留参数名
        var normalized = PATH_PARAM.matcher(path.trim()).replaceAll(match -> {
            var paramName = match.group(1).toLowerCase();
            return "_" + paramName;
        });

        // 阶段 2: 逐字符转换分隔符, 字母数字保留并小写
        var result = new StringBuilder(normalized.length());

        for (int i = 0, j = normalized.length(); i < j; i++) {
            char c = normalized.charAt(i);

            if (c == '/') {
                // / 变 _; 开头连续 / 因 result 为空而不追加, 避免前导 _
                appendUnderscore(result);
                continue;
            }

            if (c == '-' || c == '.') {
                appendUnderscore(result);
                continue;
            }

            if (c == '_') {
                // 合并连续 _ (含 / 与 {param} 替换产生的相邻 _)
                appendUnderscore(result);
                continue;
            }

            if (Character.isLetterOrDigit(c)) {
                result.append(Character.toLowerCase(c));
                continue;
            }

            // 其余特殊字符视为分隔符
            appendUnderscore(result);
        }

        trimTrailingUnderscore(result);
        return result.toString();
    }

    /**
     * 将清洗后的路径片段追加到命名结果中.
     * <p>
     * 若 {@code segment} 不以 {@code /} 开头, 会自动补前缀再清洗.
     * blank 或清洗后为空时不追加.
     * </p>
     *
     * @param result  已有命名 (如 method), 本方法会直接 mutate
     * @param segment contextPath 或 path 原始值
     */
    public static void appendSegment(StringBuilder result, String segment) {
        if (Strings.isBlank(segment)) {
            return;
        }

        // 无 leading / 的路径 (如 g2rain-basis) 补 / 后按统一规则清洗
        var sanitized = sanitizePath(segment.startsWith("/") ? segment : "/" + segment);
        if (sanitized.isEmpty()) {
            return;
        }

        // 与已有片段 (如 method) 之间加 _
        appendUnderscore(result);
        result.append(sanitized);
    }

    /**
     * 确保名称不超过 {@link #MAX_TOOL_NAME_LENGTH}, 超长时截断并追加 hash 后缀.
     * <p>
     * 截断格式: {@code {prefix}_{hash8}}, 其中 {@code hash8} 为 {@code uniquenessKey}
     * 的 SHA-256 前 8 位 hex. 同一 {@code uniquenessKey} 始终产生相同结果.
     * </p>
     *
     * @param name          策略产出的原始名称
     * @param uniquenessKey 唯一性输入 (如 {@code get|/basis|/users/{id}} 或 operationId 原文)
     * @return 长度 {@code <=} {@link #MAX_TOOL_NAME_LENGTH} 的最终 tool code
     */
    public static String ensureMaxLength(String name, String uniquenessKey) {
        if (name.length() <= MAX_TOOL_NAME_LENGTH) {
            return name;
        }

        // 超长: 保留可读前缀 + _ + 8位 hash, 总长 = 256
        var hashSuffix = hashSuffix(uniquenessKey);
        var prefixMaxLength = MAX_TOOL_NAME_LENGTH - hashSuffix.length() - 1;
        var prefix = new StringBuilder(prefixMaxLength).append(name, 0, prefixMaxLength);
        // 截断点落在 _ 上时去掉, 避免 prefix_hash 出现双 _
        trimTrailingUnderscore(prefix);
        return prefix + "_" + hashSuffix;
    }

    /**
     * 追加单个 {@code _}, 若末尾已有 {@code _} 则跳过, 避免连续下划线.
     */
    private static void appendUnderscore(StringBuilder result) {
        if (!result.isEmpty() && result.charAt(result.length() - 1) != '_') {
            result.append('_');
        }
    }

    /**
     * 去除 {@link StringBuilder} 末尾连续的 {@code _}.
     */
    private static void trimTrailingUnderscore(StringBuilder builder) {
        while (!builder.isEmpty() && builder.charAt(builder.length() - 1) == '_') {
            builder.setLength(builder.length() - 1);
        }
    }

    /**
     * 计算 SHA-256 前 {@link #HASH_SUFFIX_LENGTH} 位 hex 作为唯一性后缀.
     */
    private static String hashSuffix(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            // 取前 4 字节 → 8 位 hex
            return HexFormat.of().formatHex(hash, 0, HASH_SUFFIX_LENGTH / 2);
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(McpErrorCode.HASH_ALGORITHM_UNAVAILABLE, exception);
        }
    }
}
