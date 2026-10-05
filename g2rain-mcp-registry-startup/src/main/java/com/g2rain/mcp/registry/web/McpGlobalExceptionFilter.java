package com.g2rain.mcp.registry.web;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.exception.ExceptionProcessor;
import com.g2rain.common.utils.Strings;
import com.g2rain.web.autoconfigure.G2rainWebProperties;
import com.g2rain.web.filters.GlobalExceptionFilter;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>{@code McpGlobalExceptionFilter} 是 MCP 端点的全局异常过滤器，继承 {@link GlobalExceptionFilter}。</p>
 *
 * <p><b>职责说明：</b></p>
 * <ul>
 *     <li>对 MCP HTTP 端点（如 {@code /mcp}）捕获 Filter 链中的未处理异常。</li>
 *     <li>将异常映射为符合 MCP/JSON-RPC 规范的错误响应，而非 g2rain 统一的 {@code Result} 包装体。</li>
 *     <li>对非 MCP 请求保持父类行为，继续走标准 Result 异常兜底逻辑。</li>
 * </ul>
 *
 * <p><b>异常映射规则：</b></p>
 * <ul>
 *     <li>{@link BusinessException} → {@link McpSchema.ErrorCodes#INVALID_PARAMS}（参数/业务校验失败）。</li>
 *     <li>其他 {@link Exception} → {@link McpSchema.ErrorCodes#INTERNAL_ERROR}（系统内部错误）。</li>
 * </ul>
 *
 * <p>本 Filter 由 {@link com.g2rain.mcp.registry.config.McpWebConfiguration} 注册，
 * 优先级为 {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE}。</p>
 *
 * @see McpJsonRpcResponses
 * @see GlobalExceptionFilter
 */
@Slf4j
public class McpGlobalExceptionFilter extends GlobalExceptionFilter {

    /**
     * MCP 端点的挂载路径（已规范化，以 {@code /} 开头，无末尾斜杠）。
     * <p>用于与 {@link HttpServletRequest#getRequestURI()} 比对，判断当前请求是否属于 MCP 端点。</p>
     * <p>值来源于配置项 {@code spring.ai.mcp.server.streamable-http.mcp-endpoint}，默认 {@code /mcp}。</p>
     */
    private final String mcpMountPath;

    /**
     * 构造 MCP 全局异常过滤器。
     *
     * @param exceptionProcessor g2rain 统一异常处理器，传递给父类处理非 MCP 请求
     * @param properties         g2rain Web 配置属性，传递给父类
     * @param mcpEndpoint        MCP 端点路径配置值，将在内部通过 {@link #normalizePath(String)} 规范化
     */
    public McpGlobalExceptionFilter(ExceptionProcessor exceptionProcessor, G2rainWebProperties properties, String mcpEndpoint) {
        super(exceptionProcessor, properties);
        // 规范化端点路径，确保后续 URI 比对一致
        this.mcpMountPath = normalizePath(mcpEndpoint);
    }

    /**
     * Filter 核心逻辑：区分 MCP 与非 MCP 请求，分别采用不同的异常响应格式。
     *
     * <p><b>MCP 请求处理流程：</b></p>
     * <ol>
     *     <li>执行 Filter 链；</li>
     *     <li>捕获 {@link BusinessException} 或通用 {@link Exception}；</li>
     *     <li>在 {@code finally} 块中，若响应尚未提交，写入 JSON-RPC error 响应体。</li>
     * </ol>
     *
     * <p><b>非 MCP 请求：</b>直接委托 {@link GlobalExceptionFilter#doFilterInternal} 处理。</p>
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param chain    Filter 链
     * @throws IOException 写入响应体时可能抛出
     */
    @Override
    public void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain) throws IOException {
        // 非 MCP 端点请求走父类标准 Result 异常处理
        if (!isMcpRequest(request)) {
            super.doFilterInternal(request, response, chain);
            return;
        }

        McpError mcpError = null;
        try {
            // 继续执行后续 Filter 及 MCP 业务逻辑
            chain.doFilter(request, response);
        } catch (BusinessException e) {
            // 业务异常：参数校验、权限等业务层错误，映射为 INVALID_PARAMS
            log.error("MCP 业务异常-{}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            mcpError = McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS).message(errorMessage(e.getMessage())).build();
        } catch (Exception e) {
            // 系统异常：尝试从异常链中提取 BusinessException，否则使用默认消息
            log.error("MCP 系统异常-{}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            BusinessException businessException = ExceptionConverter.findBusinessExceptionOrDefault(e);
            mcpError = McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                .message(errorMessage(businessException.getMessage()))
                .build();
        } finally {
            // 仅在有异常且响应未提交时写入 JSON-RPC error，避免重复写或覆盖已成功响应
            if (Objects.nonNull(mcpError) && !response.isCommitted()) {
                // HTTP 500，JSON-RPC error 体由 McpJsonRpcResponses 构造
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                // 读取请求体以提取 JSON-RPC id，保证 error 与 request 配对
                byte[] jsonBytes = McpJsonRpcResponses.errorBytes(
                    mcpError.getJsonRpcError().code(),
                    mcpError.getJsonRpcError().message(),
                    readRequestBody(request)
                );
                response.setContentLength(jsonBytes.length);
                response.getOutputStream().write(jsonBytes);
                response.getOutputStream().flush();
            }
        }
    }

    /**
     * 读取 HTTP 请求体全文，用于 JSON-RPC {@code id} 提取。
     *
     * <p>使用 {@link HttpServletRequest#getReader()} 按行拼接；
     * 读取失败时返回 {@code null}，由 {@link McpJsonRpcResponses} 使用回退 id。</p>
     *
     * @param request 当前 HTTP 请求
     * @return 请求体字符串；读取失败时返回 {@code null}
     */
    private static String readRequestBody(HttpServletRequest request) {
        try (BufferedReader reader = request.getReader()) {
            // 按行读取并拼接，兼容无换行的单行 JSON 请求体
            return reader.lines().collect(Collectors.joining());
        } catch (IOException e) {
            // 请求体已被消费或 IO 异常时，无法提取 id
            return null;
        }
    }

    /**
     * 规范化错误消息，避免向客户端返回空字符串。
     *
     * @param message 原始异常消息，可能为 {@code null} 或空白
     * @return 非空错误消息；原消息为空时返回 {@code "Internal error"}
     */
    private static String errorMessage(String message) {
        return Strings.isBlank(message) ? "Internal error" : message;
    }

    /**
     * 判断当前请求是否命中 MCP 端点。
     *
     * <p>比对规则：去除末尾斜杠后的 {@code requestURI} 与
     * {@code contextPath + mcpMountPath} 完全相等。</p>
     *
     * @param request 当前 HTTP 请求
     * @return {@code true} 表示为 MCP 端点请求，应走 JSON-RPC 异常处理
     */
    private boolean isMcpRequest(HttpServletRequest request) {
        // 规范化请求 URI，消除末尾斜杠差异
        String requestUri = stripTrailingSlash(request.getRequestURI());
        // 拼接 contextPath 与 MCP 挂载路径，形成完整匹配目标
        String mcpUri = stripTrailingSlash(request.getContextPath() + mcpMountPath);
        return requestUri.equals(mcpUri);
    }

    /**
     * 规范化路径：确保以 {@code /} 开头。
     *
     * @param path 原始路径配置值，可能不含前导斜杠或为空白
     * @return 以 {@code /} 开头的路径；空白时返回 {@code "/"}
     */
    private static String normalizePath(String path) {
        if (Strings.isBlank(path)) {
            return "/";
        }

        // 补全前导斜杠，与 Servlet URI 规范一致
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * 去除路径末尾的 {@code /}（根路径 {@code /} 除外）。
     *
     * <p>用于 URI 比对时消除 {@code /mcp} 与 {@code /mcp/} 的差异。</p>
     *
     * @param path 原始路径，可能为 {@code null}
     * @return 去除末尾斜杠后的路径；{@code null} 或长度 ≤ 1 时原样返回
     */
    private static String stripTrailingSlash(String path) {
        if (Objects.isNull(path) || path.length() <= 1) {
            return path;
        }

        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
