package com.g2rain.mcp.registry.enums;

/**
 * 外部服务资源类型：扩展系统（EXTENSION）或客户系统（CUSTOMER）。
 * <p>
 * 对应表字段 {@code mcp_external_service.resource_type}。
 */
public enum ResourceType {

    /**
     * 自有系统（来自 basis 资源接口）。
     */
    BUILT_IN,

    /**
     * 扩展系统（运营公司维护，工具侧可被租户只读引用）。
     */
    EXTENSION,

    /**
     * 客户系统（本机构维护）。
     */
    CUSTOMER
}
