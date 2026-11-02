/**
 * g2rain MCP Registry 核心业务包.
 * <p>
 * 本模块把平台内的 OpenAPI 接口「暴露」为 MCP Tool, 供 LLM 客户端通过
 * {@code tools/list} 发现工具、通过 {@code tools/call} 调用工具.
 * 运行时数据来自内存注册表 {@link com.g2rain.mcp.registry.tool.ToolRegistry},
 * 配置数据来自 {@code mcp_tool_profile} 表及关联的 OpenAPI 元数据.
 * </p>
 *
 * <h2>如何阅读本包</h2>
 * <p>
 * 建议按「注册阶段 → list 链路 → call 链路」三条线理解, 下面用 {@code a->b->c} 表示调用顺序.
 * </p>
 *
 * <h3>一、Tool 注册阶段（服务启动或 profile 热更新）</h3>
 * <p>
 * 把一条 OpenAPI 操作变成内存里可 list、可 call 的 Tool:
 * </p>
 * <pre>{@code
 * McpToolProfile（name、inputSchema、openapi_document）
 *   -> ToolOperationAssembler.assemble       // 解析单接口文档, 得到 OpenApiOperation
 *   -> ToolRegistry.register
 *   -> ToolRegistry.buildRegisteredTool
 *        -> 档案 name                         // 导入时由 EndpointStrategy 生成, 如 native_g2rain_basis_get_users_id
 *        -> 档案 inputSchema                  // 导入时由 InputSchemaComposer.compose 生成
 *        -> RegisteredTool                   // 元数据 + 末级执行函数
 *   -> ToolSpecBuilder.buildSyncToolSpec
 *        -> ToolCallFilterChains.of           // RegisteredTool + 横切 Filter 排序成链
 *   -> ResolvedTool
 *   -> ToolRegistry.put                      // 写入 byCode 索引
 * }</pre>
 * <p>
 * 示例: profile 绑定 {@code sourceId=1001}（对应某个 resource_api）,
 * code 为 {@code get_order_by_id}, 注册完成后该 Tool 才会出现在 list/call 中.
 * </p>
 *
 * <h3>二、{@code tools/list} 调用链路（客户端「有哪些 Tool 可用」）</h3>
 * <pre>{@code
 * MCP 客户端 HTTP 请求
 *   -> McpSyncServer 协议入口
 *   -> McpToolHandlerPatcher 替换后的 list Handler
 *   -> McpRequestContexts.of(exchange)        // 组装 HTTP + 会话上下文
 *   -> McpToolListHandler.handle
 *   -> ToolRegistry.listTools
 *        -> byCode 中共享工具 + RuntimeToolCatalog 租户工具
 *   -> ListToolsResult                        // 返回 name / title / inputSchema 等
 * }</pre>
 * <p>
 * 原生工具权限在 {@code tools/call} 时校验，list 不按 sourceId 过滤。
 * </p>
 *
 * <h3>三、{@code tools/call} 调用链路（客户端「执行某个 Tool」）</h3>
 * <pre>{@code
 * MCP 客户端 HTTP 请求（含 tool name + arguments）
 *   -> McpSyncServer 协议入口
 *   -> McpToolHandlerPatcher 替换后的 call Handler
 *   -> McpRequestContexts.of(exchange, CallToolRequest)
 *   -> McpToolCallHandler.handle
 *   -> ToolRegistry.findToolSpecification      // 按 name 查找, 并再次校验 sourceId 权限
 *   -> SyncToolSpecification.callHandler       // 注册阶段由 ToolSpecBuilder 组装
 *        -> McpRequestContexts.of
 *        -> ToolCallFilterChain.doFilter
 *             -> [横切 ToolCallFilter ...]     // 指标、鉴权扩展等, 按 order 排序
 *             -> RegisteredTool.doFilter
 *                  -> InputSchemaComposer.decompose   // arguments -> HttpRequestSpec
 *                  -> ToolHandler.execute
 *                       -> HttpRequestEnricherChain.enrich  // Principal、User-Agent 等 Header
 *                       -> RestClient.retrieve             // 调用 lb:// 或 http:// 下游 API
 *                       -> CallToolResult                  // JSON 文本 + isError
 * }</pre>
 * <p>
 * 示例: 客户端 call {@code get_order_by_id}, arguments 为 {@code {"_params":{"orderId":"abc"}}},
 * 分解后请求 {@code GET lb://g2rain-basis/orders/abc}, 响应 JSON 原样放入 CallToolResult 返回给 LLM.
 * </p>
 *
 * <h2>子包职责速查</h2>
 * <ul>
 *   <li>{@code config} — 默认 Bean 装配、MCP list/call Handler 替换</li>
 *   <li>{@code context} — 单次 MCP 请求的统一上下文</li>
 *   <li>{@code tool} — list/call 入口、注册表、HTTP 执行、Filter 链</li>
 *   <li>{@code schema} — OpenAPI inputSchema 组合与 arguments 分解</li>
 *   <li>{@code catalog} / {@code openapi} — OpenAPI 操作元数据与命名策略</li>
 *   <li>{@code enricher} — 出站 HTTP 请求 Header 增强</li>
 *   <li>{@code error} — 网络失败时的统一错误 JSON</li>
 *   <li>{@code support} — 指标、进度通知等可替换扩展点</li>
 *   <li>{@code service} / {@code controller} / {@code dao} — profile 配置 CRUD（驱动注册阶段）</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/7/3
 */
package com.g2rain.mcp.registry;
