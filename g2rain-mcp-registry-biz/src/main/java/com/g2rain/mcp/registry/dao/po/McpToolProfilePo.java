package com.g2rain.mcp.registry.dao.po;

import com.g2rain.common.model.BasePo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * MCP Tool暴露档案表返回Po
 * 关联表名: mcp_tool_profile
 * 功能：封装实体数据，继承BasePo复用基础字段逻辑
 *
 * @author G2rain Generator
 */
@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class McpToolProfilePo extends BasePo {

    /**
     * 机构标识
     */
    private Long organId;

    /**
     * Tool名称(MCP tools/call name)
     */
    private String name;

    /**
     * Tool标题(MCP title)
     */
    private String title;

    /**
     * 资源类型[NATIVE:原生系统, EXTENSION:扩展系统, CUSTOMER:租户系统]
     */
    private String resourceType;

    /**
     * 所属服务(NATIVE→basis.service_registry.id; 其余→mcp_external_service.id)
     */
    private Long serviceId;

    /**
     * 来源标识(仅NATIVE=basis.resource_api.id)
     */
    private Long sourceId;

    /**
     * HTTP方法
     */
    private String method;

    /**
     * HTTP路径
     */
    private String path;

    /**
     * 状态[ACTIVE:启用, INACTIVE:禁用]
     */
    private String status;

    /**
     * Tool说明(给LLM)
     */
    private String description;

    /**
     * 单接口OpenAPI文档(含openapi版本、info与单个path+method, $ref已展开)
     */
    private String openapiDocument;

    /**
     * MCP inputSchema
     */
    private String inputSchema;

    /**
     * 删除标识[0:未删除, 1:已删除]
     */
    private Boolean deleteFlag;
}