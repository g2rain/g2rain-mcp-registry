package com.g2rain.mcp.registry.tool;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.mcp.registry.config.McpToolHandlerRegister;
import com.g2rain.mcp.registry.context.McpRequestContext;
import com.g2rain.mcp.registry.enums.McpErrorCode;
import io.modelcontextprotocol.spec.McpSchema;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * MCP {@code tools/call} 请求处理器.
 * <p>
 * 当 LLM 客户端要「执行某个 Tool」时, MCP 协议会发起 {@code tools/call} 请求,
 * 携带 tool {@code name} 与 {@code arguments}.
 * 本类是该请求在 g2rain MCP Registry 内的<strong>唯一入口</strong>.
 * </p>
 *
 * <h2>调用链路</h2>
 * <pre>
 * McpToolHandlerPatcher
 *   -> McpRequestContexts.of(exchange, CallToolRequest)
 *   -> McpToolCallHandler.handle
 *   -> ToolRegistry.findToolSpecification
 *   -> SyncToolSpecification.callHandler
 *   -> ToolCallFilterChain -> RegisteredTool -> ToolHandler
 *   -> CallToolResult
 * </pre>
 *
 * <h2>处理步骤</h2>
 * <ol>
 *   <li>校验 {@link McpRequestContext#callToolRequest()} 非空</li>
 *   <li>按 {@code name} 从 {@link ToolRegistry} 查找 Tool（含权限校验）</li>
 *   <li>找不到 → 抛出 {@link BusinessException}({@link McpErrorCode#UNKNOWN_TOOL})；原生工具无权限在查找时抛出未授权</li>
 *   <li>找到 → 执行注册阶段组装好的 {@code callHandler}, 得到 {@link McpSchema.CallToolResult}</li>
 * </ol>
 *
 * <h2>职责边界</h2>
 * <ul>
 *   <li><strong>负责</strong>: Tool 查找、触发 callHandler、包装 {@link Mono} 响应</li>
 *   <li><strong>不负责</strong>: arguments 分解、HTTP 调用、Filter 链组装 — 这些在 {@code callHandler} 内部完成</li>
 * </ul>
 *
 * <h2>示例</h2>
 * <p>
 * 客户端 call {@code get_order_by_id}, arguments {@code {"_params":{"orderId":"abc-123"}}}:
 * </p>
 * <ul>
 *   <li>Tool 存在且有权限 → 下游 HTTP 返回订单 JSON, 封装为 CallToolResult 给 LLM</li>
 *   <li>Tool 不存在 → {@link McpErrorCode#UNKNOWN_TOOL}</li>
 *   <li>原生工具无权限 → 未授权，不按未知工具吞掉</li>
 * </ul>
 *
 * @author alpha
 * @see ToolRegistry#findToolSpec(McpRequestContext, String)
 * @see McpToolHandlerRegister
 * @see McpToolListHandler
 * @since 2026/6/29
 */
@Component
@NullMarked
public class McpToolCallHandler {

    /**
     * Tool 内存注册表: 按 name 查找可执行的 {@code SyncToolSpecification}.
     * <p>
     * 与 {@link McpToolListHandler} 共用, 保证 list 与 call 看到同一套 Tool.
     * </p>
     */
    private final ToolRegistry toolRegistry;

    /**
     * 构造 {@code tools/call} 处理器.
     *
     * @param toolRegistry tool 注册表, 由 Spring 容器注入
     */
    public McpToolCallHandler(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 处理 MCP {@code tools/call} 请求.
     * <p>
     * 查找 Tool 并执行其 {@code callHandler}; 找不到时抛出 {@link BusinessException}.
     * </p>
     *
     * @param context 当前 MCP 请求上下文; {@code callToolRequest} 与 {@code syncServerExchange} 由 Patcher 填充
     * @return Tool 执行结果
     * @throws BusinessException 请求缺失或 Tool 不存在/无权限
     */
    public Mono<McpSchema.CallToolResult> handle(McpRequestContext context) {
        var callToolRequest = context.callToolRequest();
        if (Objects.isNull(callToolRequest)) {
            throw new BusinessException(McpErrorCode.MISSING_CALL_TOOL_REQUEST);
        }

        var toolSpec = toolRegistry.findToolSpec(context, callToolRequest.name()).orElseThrow(() ->
            new BusinessException(McpErrorCode.UNKNOWN_TOOL, callToolRequest.name())
        );

        return Mono.just(toolSpec.callHandler().apply(
            context.syncServerExchange(), callToolRequest
        ));
    }
}
