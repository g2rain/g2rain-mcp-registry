package com.g2rain.mcp.registry.dao.po;

import com.g2rain.common.model.BasePo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * MCP 外部服务表返回Po
 * 关联表名: mcp_external_service
 * 功能：封装实体数据，继承BasePo复用基础字段逻辑
 *
 * @author G2rain Generator
 */
@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class McpExternalServicePo extends BasePo {

    /**
     * 机构标识
     */
    private Long organId;

    /**
     * 服务编码
     */
    private String code;

    /**
     * 服务名称
     */
    private String name;

    /**
     * 调用基址(http/https)
     */
    private String endpoint;

    /**
     * 上下文路径
     */
    private String contextPath;

    /**
     * 资源类型[EXTENSION:扩展系统, CUSTOMER:客户系统]
     */
    private String resourceType;

    /**
     * 状态[ACTIVE:启用, INACTIVE:禁用]
     */
    private String status;

    /**
     * 说明
     */
    private String description;

    /**
     * 删除标识[0:未删除, 1:已删除]
     */
    private Boolean deleteFlag;
}
