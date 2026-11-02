package com.g2rain.mcp.registry.enums;

/**
 * 工具资源类型：谷雨原生（NATIVE）、扩展系统（EXTENSION）、租户系统（CUSTOMER）。
 * <p>
 * 对应表字段 {@code mcp_tool_profile.resource_type}、{@code mcp_external_service.resource_type}；
 * 枚举值小写即工具名前缀（{@code native_}、{@code extension_}、{@code customer_}）。
 * </p>
 */
public enum ResourceType {

    /**
     * 谷雨原生（来自 basis 资源接口），全局可见。
     */
    NATIVE,

    /**
     * 扩展系统（运营公司维护），全局可见。
     */
    EXTENSION,

    /**
     * 租户系统（本机构维护），仅本机构可见。
     */
    CUSTOMER
}
