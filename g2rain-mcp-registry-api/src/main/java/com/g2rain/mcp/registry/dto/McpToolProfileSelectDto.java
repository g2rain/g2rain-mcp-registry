package com.g2rain.mcp.registry.dto;

import com.g2rain.common.model.BaseSelectListDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * MCP Tool暴露档案表查询入参DTO
 * 用于McpToolProfileDao.selectList方法的条件筛选
 * 表名: mcp_tool_profile
 *
 * @author G2rain Generator
 */
@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "MCP Tool暴露档案表查询入参 DTO")
public class McpToolProfileSelectDto extends BaseSelectListDto {

    /**
     * 机构标识
     */
    @Schema(description = "机构标识")
    private Long organId;

    /**
     * Tool名称(MCP tools/call name)
     */
    @Schema(description = "Tool名称(MCP tools/call name)")
    private String name;

    /**
     * Tool标题(MCP title)
     */
    @Schema(description = "Tool标题(MCP title)")
    private String title;

    /**
     * 资源类型[BUILT_IN:自有系统, EXTENSION:扩展系统, CUSTOMER:客户系统]
     */
    @Schema(description = "资源类型[BUILT_IN:自有系统, EXTENSION:扩展系统, CUSTOMER:客户系统]")
    private String resourceType;

    /**
     * 所属服务编码(BUILT_IN→basis.service_registry; 其余→mcp_external_service.code)
     */
    @Schema(description = "所属服务编码(BUILT_IN→basis.service_registry; 其余→mcp_external_service.code)")
    private String serviceCode;

    /**
     * 来源标识(仅BUILT_IN=basis.resource_api.id)
     */
    @Schema(description = "来源标识(仅BUILT_IN=basis.resource_api.id)")
    private Long sourceId;

    /**
     * HTTP方法
     */
    @Schema(description = "HTTP方法")
    private String method;

    /**
     * HTTP路径
     */
    @Schema(description = "HTTP路径")
    private String path;

    /**
     * 状态[ACTIVE:启用, INACTIVE:禁用]
     */
    @Schema(description = "状态[ACTIVE:启用, INACTIVE:禁用]")
    private String status;

    /**
     * Tool说明(给LLM)
     */
    @Schema(description = "Tool说明(给LLM)")
    private String description;

    /**
     * OpenAPI Operation(compose/decompose)
     */
    @Schema(description = "OpenAPI Operation(compose/decompose)")
    private String openapiOperation;

    /**
     * MCP inputSchema
     */
    @Schema(description = "MCP inputSchema")
    private String inputSchema;
}
