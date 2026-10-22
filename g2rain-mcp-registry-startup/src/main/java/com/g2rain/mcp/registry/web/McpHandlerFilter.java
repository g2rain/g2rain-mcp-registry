package com.g2rain.mcp.registry.web;


import com.g2rain.common.enums.SessionType;
import com.g2rain.common.web.PrincipalContextHolder;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Objects;

/**
 * <p>{@code McpHandlerFilter} 是 MCP {@link org.springframework.web.servlet.function.RouterFunction}
 * 的登录态校验过滤器。</p>
 *
 * <p><b>职责说明：</b></p>
 * <ul>
 *     <li>在 MCP 请求进入 Spring AI 业务 Handler 之前，校验当前线程的登录上下文。</li>
 *     <li>未通过校验时返回 HTTP {@code 401} 及 JSON-RPC error 响应体，而非 HTML 或 Result 包装。</li>
 *     <li>通过校验时透传请求至下游 Handler。</li>
 * </ul>
 *
 * <p><b>认证策略（会话类型对应的主体 ID 齐全才视为已认证）：</b></p>
 * <ul>
 *     <li>用户会话（{@link SessionType#USER}）且 {@link PrincipalContextHolder#getUserId()} 为正。</li>
 *     <li>通行证会话（{@link SessionType#PASSPORT}）且 {@link PrincipalContextHolder#getPassportId()} 为正。</li>
 *     <li>会员会话（{@link SessionType#MEMBER}）且 {@link PrincipalContextHolder#getMemberId()} 为正。</li>
 *     <li>匿名会话（{@link SessionType#ANONYMOUS}）：机构与访客 ID（userId）为正，且无账号、会员主体；
 *     能调用哪些工具由 ANONYMOUS 控制单元决定。</li>
 * </ul>
 *
 * <p>本 Filter 由 {@link com.g2rain.mcp.registry.config.McpWebConfiguration} 注册，
 * 并通过 {@code RouterFunction.filter()} 挂载到 Streamable / Stateless 两种传输模式的路由上。</p>
 *
 * @see McpJsonRpcResponses
 * @see PrincipalContextHolder
 */
public class McpHandlerFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    /**
     * 未认证时返回给客户端的 JSON-RPC 错误消息。
     * <p>配合 {@link McpSchema.ErrorCodes#INVALID_REQUEST} 使用，提示客户端提供有效凭证。</p>
     */
    private static final String UNAUTHORIZED_MESSAGE = "Authentication required. Please provide valid credentials.";

    /**
     * Filter 入口：校验登录态，未通过则构造 401 JSON-RPC error 响应。
     *
     * <p><b>处理流程：</b></p>
     * <ol>
     *     <li>调用 {@link #isAuthenticated()} 检查当前线程 Principal 上下文；</li>
     *     <li>已认证 → 执行 {@code next.handle(request)} 继续 MCP 业务处理；</li>
     *     <li>未认证 → 读取请求体提取 JSON-RPC {@code id}，构造 error 响应返回。</li>
     * </ol>
     *
     * @param request 当前 MCP HTTP 请求
     * @param next    下游 Handler 函数
     * @return MCP 业务响应，或 401 JSON-RPC error 响应
     * @throws Exception 下游 Handler 执行时可能抛出的异常
     */
    @Override
    public ServerResponse filter(@NonNull ServerRequest request, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
        // 登录态校验通过，继续 MCP 协议处理
        if (isAuthenticated()) {
            return next.handle(request);
        }

        // 构造 MCP 标准错误对象：INVALID_REQUEST + 未认证提示消息
        McpError mcpError = McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
            .message(UNAUTHORIZED_MESSAGE)
            .build();
        // 读取请求体以便 error 响应携带与 request 相同的 id
        String requestBody = readRequestBody(request);
        byte[] body = McpJsonRpcResponses.errorBytes(
            mcpError.getJsonRpcError().code(),
            mcpError.getJsonRpcError().message(),
            requestBody
        );
        // 返回 HTTP 401，Content-Type 为 application/json
        return ServerResponse.status(HttpStatus.UNAUTHORIZED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
    }

    /**
     * 读取 MCP 请求体字符串，用于 JSON-RPC {@code id} 提取。
     *
     * <p>使用 {@link ServerRequest#body(Class)} 读取；
     * 读取失败（如请求体已被消费）时返回 {@code null}。</p>
     *
     * @param request 当前 MCP HTTP 请求
     * @return 请求体字符串；读取失败时返回 {@code null}
     */
    private static String readRequestBody(ServerRequest request) {
        try {
            return request.body(String.class);
        } catch (Exception ignored) {
            // 请求体不可读时回退，McpJsonRpcResponses 将使用固定 id
            return null;
        }
    }

    /**
     * 判断当前线程是否具备访问 MCP 端点的有效登录上下文：会话类型对应的主体 ID 须为正。
     *
     * <p><b>判定逻辑：</b></p>
     * <ol>
     *     <li>用户会话：userId 为正；</li>
     *     <li>通行证会话：passportId 为正；</li>
     *     <li>会员会话：memberId 为正；</li>
     *     <li>匿名会话：organId 与访客 ID（userId）为正，且无账号、会员主体；</li>
     *     <li>其他情况 → 未认证。</li>
     * </ol>
     *
     * @return {@code true} 表示已通过认证校验，可继续处理 MCP 请求
     */
    private boolean isAuthenticated() {
        return switch (PrincipalContextHolder.getSessionType()) {
            case USER -> isPositive(PrincipalContextHolder.getUserId());
            case PASSPORT -> isPositive(PrincipalContextHolder.getPassportId());
            case MEMBER -> isPositive(PrincipalContextHolder.getMemberId());
            case ANONYMOUS -> isPositive(PrincipalContextHolder.getOrganId())
                && isPositive(PrincipalContextHolder.getUserId())
                && Objects.isNull(PrincipalContextHolder.getPassportId())
                && Objects.isNull(PrincipalContextHolder.getMemberId());
            case null -> false;
        };
    }

    private static boolean isPositive(Long id) {
        return Objects.nonNull(id) && id > 0L;
    }
}
