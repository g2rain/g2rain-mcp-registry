package com.g2rain.mcp.registry.dto;

import com.g2rain.common.model.BaseDto;
import com.g2rain.common.validation.CreateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MCP Tool暴露档案表查询DTO
 * 表名: mcp_tool_profile
 *
 * @author G2rain Generator
 */
@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "MCP Tool暴露档案表 DTO")
public class McpToolProfileDto extends BaseDto {

    /**
     * 机构标识
     */
    @NotNull(groups = CreateGroup.class)
    @Schema(description = "机构标识（新增必填）")
    private Long organId;

    /**
     * Tool名称(MCP tools/call name)
     */
    @NotBlank(groups = CreateGroup.class)
    @Size(max = 256)
    @Schema(description = "Tool名称(MCP tools/call name)（新增必填）", maxLength = 256)
    private String name;

    /**
     * Tool标题(MCP title)
     */
    @NotBlank(groups = CreateGroup.class)
    @Size(max = 128)
    @Schema(description = "Tool标题(MCP title)（新增必填）", maxLength = 128)
    private String title;

    /**
     * 资源类型[NATIVE:原生系统, EXTENSION:扩展系统, CUSTOMER:租户系统]
     */
    @NotBlank(groups = CreateGroup.class)
    @Size(max = 32)
    @Schema(description = "资源类型[NATIVE:原生系统, EXTENSION:扩展系统, CUSTOMER:租户系统]（新增必填）", maxLength = 32)
    private String resourceType;

    /**
     * 所属服务(NATIVE→basis.service_registry.id; 其余→mcp_external_service.id)
     */
    @NotNull(groups = CreateGroup.class)
    @Schema(description = "所属服务(NATIVE→basis.service_registry.id; 其余→mcp_external_service.id)（新增必填）")
    private Long serviceId;

    /**
     * 来源标识(仅NATIVE=basis.resource_api.id)
     */
    @Schema(description = "来源标识(仅NATIVE=basis.resource_api.id)")
    private Long sourceId;

    /**
     * HTTP方法
     */
    @NotBlank(groups = CreateGroup.class)
    @Size(max = 32)
    @Schema(description = "HTTP方法（新增必填）", maxLength = 32)
    private String method;

    /**
     * HTTP路径
     */
    @NotBlank(groups = CreateGroup.class)
    @Size(max = 512)
    @Schema(description = "HTTP路径（新增必填）", maxLength = 512)
    private String path;

    /**
     * Tool说明(给LLM)
     */
    @Size(max = 500)
    @Schema(description = "Tool说明(给LLM)", maxLength = 500)
    private String description;

    /**
     * 单接口OpenAPI文档(含openapi版本、info与单个path+method, $ref已展开)
     */
    @NotBlank(groups = CreateGroup.class)
    @Size(max = 1073741824)
    @Schema(description = "单接口OpenAPI文档(含openapi版本、info与单个path+method, $ref已展开)（新增必填）", maxLength = 1073741824)
    private String openapiDocument;

    /**
     * MCP inputSchema
     */
    @NotBlank(groups = CreateGroup.class)
    @Size(max = 1073741824)
    @Schema(description = "MCP inputSchema（新增必填）", maxLength = 1073741824)
    private String inputSchema;
}
