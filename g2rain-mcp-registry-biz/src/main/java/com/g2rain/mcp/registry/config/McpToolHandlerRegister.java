package com.g2rain.mcp.registry.config;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.mcp.registry.context.McpRequestContexts;
import com.g2rain.mcp.registry.enums.McpErrorCode;
import com.g2rain.mcp.registry.tool.McpToolCallHandler;
import com.g2rain.mcp.registry.tool.McpToolListHandler;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpRequestHandler;
import io.modelcontextprotocol.server.McpStatelessRequestHandler;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.DefaultMcpStreamableServerSessionFactory;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * g2rain MCP Registry 对 MCP Server list/call Handler 的替换器.
 * <p>
 * Spring AI MCP 启动时会创建 {@link McpSyncServer}, 其内部自带默认的 {@code tools/list}
 * 与 {@code tools/call} 处理逻辑（基于启动时静态注册的 Tool 列表）.
 * g2rain 采用<strong>中心化内存注册表</strong> {@link com.g2rain.mcp.registry.tool.ToolRegistry},
 * Tool 在运行时按 profile 动态加载、按权限过滤, 因此必须在 Server 初始化完成后,
 * 把默认 Handler 替换为 {@link com.g2rain.mcp.registry.tool.McpToolListHandler}
 * 与 {@link com.g2rain.mcp.registry.tool.McpToolCallHandler}.
 * </p>
 *
 * <h2>工作原理</h2>
 * <p>
 * 实现 {@link BeanPostProcessor}, 在 MCP Server Bean 初始化后通过反射写入内部 {@code requestHandlers} Map.
 * 当前项目使用 {@code STREAMABLE} 协议, 走 {@link McpSyncServer} 分支.
 * </p>
 *
 * <h2>替换后的 list 链路</h2>
 * <pre>
 * SDK list 回调
 *   -> McpRequestContexts.of(exchange)
 *   -> McpToolListHandler.handle
 * </pre>
 *
 * <h2>替换后的 call 链路</h2>
 * <pre>
 * SDK call 回调（params 反序列化为 CallToolRequest）
 *   -> McpRequestContexts.of(exchange, CallToolRequest)
 *   -> McpToolCallHandler.handle
 * </pre>
 *
 * <h2>失败策略</h2>
 * <p>
 * 反射或 Handler 替换失败时立即抛 {@link IllegalStateException}, 避免带着默认 Handler 静默启动
 * （那样 list/call 会走静态 Tool 列表, 与注册表不一致）.
 * </p>
 *
 * @author alpha
 * @see McpRequestContexts
 * @see McpToolListHandler
 * @see McpToolCallHandler
 * @since 2026/6/29
 */
@Slf4j
@Component
@NullMarked
public class McpToolHandlerRegister implements BeanPostProcessor {

    /**
     * 自定义 {@code tools/list} 处理器, 延迟从容器获取以避免循环依赖.
     */
    private final ObjectProvider<McpToolListHandler> listHandler;

    /**
     * 自定义 {@code tools/call} 处理器, 延迟从容器获取以避免循环依赖.
     */
    private final ObjectProvider<McpToolCallHandler> callHandler;

    /**
     * MCP 专用 JSON 映射器, 用于将 SDK 回调中的 params 反序列化为 {@link McpSchema.CallToolRequest}.
     */
    private final McpJsonMapper jsonMapper;

    /**
     * 构造 Handler 替换器.
     *
     * @param listHandler {@code tools/list} 处理器提供者
     * @param callHandler {@code tools/call} 处理器提供者
     * @param jsonMapper  与 MCP Server 共用的 {@code mcpServerJsonMapper}, 保证序列化规则一致
     */
    public McpToolHandlerRegister(
        ObjectProvider<McpToolListHandler> listHandler,
        ObjectProvider<McpToolCallHandler> callHandler,
        @Lazy @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper
    ) {
        this.listHandler = listHandler;
        this.callHandler = callHandler;
        // 包装 Spring 的 JsonMapper 为 MCP SDK 可用的 McpJsonMapper
        this.jsonMapper = new JacksonMcpJsonMapper(jsonMapper);
    }

    /**
     * Bean 初始化后的后置处理: 识别 MCP Server Bean 并替换 tool handler.
     *
     * @param bean     待处理的 Spring Bean
     * @param beanName Bean 名称
     * @return 原 Bean 实例 (不做包装替换)
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // STREAMABLE / SSE 等有会话模式: 创建的是 McpSyncServer (当前项目默认走此分支)
        if (bean instanceof McpSyncServer syncServer) {
            try {
                // 从容器解析自定义 handler 实例
                var list = listHandler.getObject();
                var call = callHandler.getObject();
                RequestHandlers.replaceStreamable(
                    syncServer,
                    // tools/list: 包装 exchange 并构造 McpRequestContext, 委托给 McpToolListHandler
                    (ex, params) -> list.handle(McpRequestContexts.of(
                        new McpSyncServerExchange(ex)
                    ), resolveAllowedTools(params)),
                    // tools/call: 额外将 params 转为 CallToolRequest 后再委托
                    (ex, params) -> call.handle(McpRequestContexts.of(
                        new McpSyncServerExchange(ex),
                        toCallToolRequest(params)
                    ))
                );

                log.info("Replaced MCP tool handlers on McpSyncServer bean '{}'", beanName);
            } catch (RuntimeException exception) {
                // 反射或 handler 替换失败时 fail-fast, 避免带着 SDK 默认 handler 静默启动
                throw new BusinessException(
                    McpErrorCode.MCP_HANDLER_PATCH_FAILED,
                    null,
                    new Object[]{beanName},
                    null,
                    exception
                );
            }

            return bean;
        }

        // STATELESS 无会话模式: 创建的是 McpStatelessSyncServer (当前项目未启用, 预留兼容)
        if (bean instanceof McpStatelessSyncServer statelessServer) {
            try {
                var list = listHandler.getObject();
                var call = callHandler.getObject();
                RequestHandlers.replaceStateless(
                    statelessServer,
                    // tools/list: 使用 McpTransportContext 构造上下文 (无 exchange)
                    (ctx, params) -> list.handle(McpRequestContexts.of(ctx),
                        resolveAllowedTools(params)
                    ),
                    // tools/call: 解析 params 并构造完整上下文
                    (ctx, params) -> call.handle(McpRequestContexts.of(
                        ctx, toCallToolRequest(params)
                    ))
                );

                log.info("Replaced MCP tool handlers on McpStatelessSyncServer bean '{}'", beanName);
            } catch (RuntimeException exception) {
                throw new BusinessException(
                    McpErrorCode.MCP_HANDLER_PATCH_FAILED,
                    null,
                    new Object[]{beanName},
                    null,
                    exception
                );
            }

            return bean;
        }

        // 非 MCP Server Bean, 原样返回
        return bean;
    }

    /**
     * 从 MCP JSON-RPC params 中解析 G2Rain 扩展字段 {@code com.g2rain/allowed_tools}.
     *
     * <p>
     * 用于 {@code tools/list} 请求，根据 Agent Tool Binding 传递的工具白名单，
     * 限制 MCP Server 返回的可用工具范围。
     * </p>
     *
     * <p>
     * params 来源于 MCP SDK handler 回调，通常为 Map 结构。
     * 由于扩展字段属于自定义 meta 数据，无法通过 MCP 标准类型直接映射，
     * 因此需要手动解析并过滤非法类型。
     * </p>
     *
     * @param params SDK 传入的 JSON-RPC params
     * @return 允许使用的 Tool name 列表，参数不存在或格式错误时返回空列表
     */
    private Set<String> resolveAllowedTools(@Nullable Object params) {
        return Optional.ofNullable(params)
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(map -> map.get("_meta"))
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(meta -> meta.get("com.g2rain/allowed_tools"))
            .filter(List.class::isInstance)
            .map(value -> (List<?>) value)
            .stream()
            .flatMap(Collection::stream)
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 将 SDK handler 回调中的原始 params 对象转换为强类型 {@link McpSchema.CallToolRequest}.
     *
     * @param params SDK 传入的 JSON-RPC params, 通常为 Map 结构
     * @return 反序列化后的 CallToolRequest
     */
    private McpSchema.CallToolRequest toCallToolRequest(Object params) {
        // 使用与 MCP Server 相同的 JsonMapper, 避免字段命名/类型与协议不一致
        return jsonMapper.convertValue(params, new TypeRef<>() {
        });
    }

    /**
     * 反射操作 SDK 内部 requestHandlers 的工具类.
     * <p>
     * 封装 STREAMABLE 与 STATELESS 两种 Server 内部对象链路的字段读取与 handler 写入.
     * </p>
     */
    private static final class RequestHandlers {

        /**
         * 私有构造, 禁止实例化.
         */
        private RequestHandlers() {

        }

        /**
         * 替换 STREAMABLE {@link McpSyncServer} 的 list/call handler.
         *
         * @param s MCP 同步服务端实例
         * @param l 新的 {@code tools/list} handler
         * @param c 新的 {@code tools/call} handler
         */
        static void replaceStreamable(McpSyncServer s, McpRequestHandler<McpSchema.ListToolsResult> l, McpRequestHandler<McpSchema.CallToolResult> c) {
            // 沿 SDK 内部对象链定位 requestHandlers Map
            var handlers = streamable(s);
            // 按 MCP 协议 method 名覆盖 list 与 call 两个入口
            handlers.put(McpSchema.METHOD_TOOLS_LIST, l);
            handlers.put(McpSchema.METHOD_TOOLS_CALL, c);
        }

        /**
         * 替换 STATELESS {@link McpStatelessSyncServer} 的 list/call handler.
         *
         * @param s MCP 无状态同步服务端实例
         * @param l 新的 {@code tools/list} handler
         * @param c 新的 {@code tools/call} handler
         */
        static void replaceStateless(McpStatelessSyncServer s, McpStatelessRequestHandler<McpSchema.ListToolsResult> l, McpStatelessRequestHandler<McpSchema.CallToolResult> c) {
            // STATELESS 内部结构与 STREAMABLE 不同, 走独立字段链
            var handlers = stateless(s);
            handlers.put(McpSchema.METHOD_TOOLS_LIST, l);
            handlers.put(McpSchema.METHOD_TOOLS_CALL, c);
        }

        /**
         * 从 {@link McpSyncServer} 解析 STREAMABLE 模式下的 requestHandlers Map.
         * <p>
         * 访问路径: syncServer → asyncServer → mcpTransportProvider → sessionFactory → requestHandlers.
         * </p>
         *
         * @param syncServer MCP 同步服务端
         * @return method 名到 handler 的映射表 (可写)
         */
        static Map<String, McpRequestHandler<?>> streamable(McpSyncServer syncServer) {
            // 取得底层 async server
            var asyncServer = syncServer.getAsyncServer();
            // 读取 transport provider (WebMvc Streamable 实现)
            var transportProvider = readField(asyncServer, "mcpTransportProvider");
            // 读取 session 工厂, 负责创建会话级 handler 表
            var sessionFactory = readField(transportProvider, "sessionFactory");
            return resolve(sessionFactory);
        }

        /**
         * 从 {@link McpStatelessSyncServer} 解析 STATELESS 模式下的 requestHandlers Map.
         * <p>
         * 访问路径: statelessServer → asyncServer → mcpTransportProvider → mcpHandler → requestHandlers.
         * </p>
         *
         * @param statelessServer MCP 无状态同步服务端
         * @return method 名到 handler 的映射表 (可写)
         */
        static Map<String, McpStatelessRequestHandler<?>> stateless(McpStatelessSyncServer statelessServer) {
            // STATELESS server 的 asyncServer 为 private 字段, 需反射读取
            var asyncServer = readField(statelessServer, "asyncServer");
            var transportProvider = readField(asyncServer, "mcpTransportProvider");
            // STATELESS 在 transport 上挂的是 mcpHandler 而非 sessionFactory
            var mcpHandler = readField(transportProvider, "mcpHandler");
            return readField(mcpHandler, "requestHandlers");
        }

        /**
         * 通过反射读取目标对象的指定字段 (含父类查找).
         *
         * @param target    目标对象
         * @param fieldName 字段名
         * @param <T>       期望的字段类型
         * @return 字段值
         * @throws BusinessException 字段不存在或无法访问时抛出
         */
        @SuppressWarnings("unchecked")
        private static <T> T readField(Object target, String fieldName) {
            try {
                // 在当前类及父类中查找字段
                var field = findField(target.getClass(), fieldName);
                // SDK 内部字段多为 private, 需要 setAccessible
                field.setAccessible(true);
                return (T) field.get(target);
            } catch (ReflectiveOperationException exception) {
                throw new BusinessException(
                    McpErrorCode.MCP_REFLECTION_FAILED,
                    null,
                    new Object[]{fieldName, target.getClass().getName()},
                    null,
                    exception
                );
            }
        }

        /**
         * 在类继承链上查找声明的字段.
         *
         * @param type      起始类
         * @param fieldName 字段名
         * @return 找到的 Field 对象
         * @throws NoSuchFieldException 整条继承链上均未找到时抛出
         */
        private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
            var current = type;
            // 沿 super class 向上查找, 直到 Object
            while (Objects.nonNull(current)) {
                try {
                    return current.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                    // 当前类未声明该字段, 继续向父类查找
                    current = current.getSuperclass();
                }
            }

            throw new NoSuchFieldException(fieldName);
        }

        /**
         * 从 sessionFactory 实例解析 requestHandlers Map.
         * <p>
         * 当前仅支持 {@link DefaultMcpStreamableServerSessionFactory},
         * 与 Spring AI WebMvc STREAMABLE 传输的默认实现一致.
         * </p>
         *
         * @param sessionFactory SDK session 工厂实例
         * @return requestHandlers 映射表
         * @throws BusinessException sessionFactory 类型不受支持时抛出
         */
        private static Map<String, McpRequestHandler<?>> resolve(Object sessionFactory) {
            if (sessionFactory instanceof DefaultMcpStreamableServerSessionFactory streamableFactory) {
                // 默认 Streamable 工厂在 requestHandlers 字段上维护 method → handler 映射
                return readField(streamableFactory, "requestHandlers");
            }

            // 非预期 factory 类型, 避免写入错误对象导致运行时行为不确定
            throw new BusinessException(
                McpErrorCode.MCP_SESSION_FACTORY_UNSUPPORTED,
                sessionFactory.getClass().getName()
            );
        }
    }
}
