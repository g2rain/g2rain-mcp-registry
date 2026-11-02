package com.g2rain.mcp.registry.utils;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.enums.McpErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * MCP 工具名命名的公共工具类，供 {@link com.g2rain.mcp.registry.openapi.naming.EndpointStrategy} 使用.
 * <p>
 * 封装三类能力:
 * </p>
 * <ol>
 *   <li><strong>清洗</strong> — {@link #sanitize(String)}（服务标题）/ {@link #sanitizePath(String)}（路径），
 *       只保留 ASCII 字母数字，驼峰边界与分隔符转 {@code _}，统一小写</li>
 *   <li><strong>长度限制</strong> — {@link #ensureMaxLength(String, String)}，上限 64（OpenAI 兼容接口函数名上限）</li>
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
     * 工具名最大长度：OpenAI 兼容接口要求函数名不超过 64 个字符.
     */
    private static final int MAX_TOOL_NAME_LENGTH = 64;

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
     * 将文本清洗为小写下划线 identifier.
     * <p>
     * 只保留 ASCII 字母数字；驼峰边界插入 {@code _}（{@code serviceCode} → {@code service_code}，
     * {@code HTTPServer} → {@code http_server}）；其余字符（含 {@code /}、{@code -}、{@code .}、空格、非 ASCII）
     * 视为分隔符；连续 {@code _} 合并，去掉首尾 {@code _}.
     * </p>
     *
     * @param text 原始文本，如 {@code g2rain-basis}；blank 时返回空串
     * @return 清洗后的片段；无可保留字符时返回空串
     */
    public static String sanitize(String text) {
        if (Strings.isBlank(text)) {
            return "";
        }

        String source = text.trim();
        var result = new StringBuilder(source.length() + 8);
        for (int i = 0, j = source.length(); i < j; i++) {
            char c = source.charAt(i);
            if (!isAsciiLetterOrDigit(c)) {
                appendUnderscore(result);
                continue;
            }

            if (isCamelBoundary(source, i)) {
                appendUnderscore(result);
            }
            result.append(Character.toLowerCase(c));
        }

        trimTrailingUnderscore(result);
        return result.toString();
    }

    /**
     * 将 OpenAPI 路径清洗为小写下划线 identifier.
     * <p>
     * 先把路径参数模板 {@code {name}}、{@code {name:regex}} 替换为参数名，再按 {@link #sanitize(String)} 清洗.
     * </p>
     *
     * @param path 原始路径，如 {@code /resource_api/{serviceCode}/import}；blank 时返回空串
     * @return 清洗后的片段，如 {@code resource_api_service_code_import}；无可保留字符时返回空串
     */
    public static String sanitizePath(String path) {
        if (Strings.isBlank(path)) {
            return "";
        }

        // {id} / {id:regex} → /id，只保留参数名，大小写留给驼峰拆分
        String normalized = PATH_PARAM.matcher(path.trim()).replaceAll(match -> "/" + match.group(1));
        return sanitize(normalized);
    }

    /**
     * 确保名称不超过 {@link #MAX_TOOL_NAME_LENGTH}, 超长时截断并追加 hash 后缀.
     * <p>
     * 截断格式: {@code {prefix}_{hash8}}, 其中 {@code hash8} 为 {@code uniquenessKey}
     * 的 SHA-256 前 8 位 hex. 同一 {@code uniquenessKey} 始终产生相同结果.
     * </p>
     *
     * @param name          策略产出的原始名称
     * @param uniquenessKey 唯一性输入 (如 {@code native|g2rain_basis|get|/users/{id}})
     * @return 长度 {@code <=} {@link #MAX_TOOL_NAME_LENGTH} 的最终 tool code
     */
    public static String ensureMaxLength(String name, String uniquenessKey) {
        if (name.length() <= MAX_TOOL_NAME_LENGTH) {
            return name;
        }

        // 超长: 保留可读前缀 + _ + 8位 hash, 总长 = MAX_TOOL_NAME_LENGTH
        var hashSuffix = hashSuffix(uniquenessKey);
        var prefixMaxLength = MAX_TOOL_NAME_LENGTH - hashSuffix.length() - 1;
        var prefix = new StringBuilder(prefixMaxLength).append(name, 0, prefixMaxLength);
        // 截断点落在 _ 上时去掉, 避免 prefix_hash 出现双 _
        trimTrailingUnderscore(prefix);
        return prefix + "_" + hashSuffix;
    }

    /**
     * ASCII 字母或数字.
     */
    private static boolean isAsciiLetterOrDigit(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /**
     * 大写字母处是否为驼峰边界：前一个是小写或数字（{@code userId}），
     * 或前一个是大写且后一个是小写（{@code HTTPServer} 的 {@code S}）.
     */
    private static boolean isCamelBoundary(String text, int index) {
        char c = text.charAt(index);
        if (c < 'A' || c > 'Z' || index == 0) {
            return false;
        }

        char prev = text.charAt(index - 1);
        if ((prev >= 'a' && prev <= 'z') || (prev >= '0' && prev <= '9')) {
            return true;
        }

        boolean prevUpper = prev >= 'A' && prev <= 'Z';
        if (!prevUpper || index + 1 >= text.length()) {
            return false;
        }

        char next = text.charAt(index + 1);
        return next >= 'a' && next <= 'z';
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
