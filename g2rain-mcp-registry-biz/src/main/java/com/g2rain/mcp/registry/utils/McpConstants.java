package com.g2rain.mcp.registry.utils;

/**
 * mcp-registry 内部共用常量。
 *
 * @author alpha
 * @since 2026/10/27
 */
public final class McpConstants {

    /**
     * 出站请求属性：工具资源类型（NATIVE / EXTENSION / CUSTOMER）。
     * <p>由工具调用设置，出站拦截器读取用于计量、审计；只在进程内传递，不写入 header。</p>
     */
    public static final String RESOURCE_TYPE_ATTRIBUTE = "resourceType";

    private McpConstants() {

    }
}
