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
 * MCP外部服务表查询DTO
 * 表名: mcp_external_service
 *
 * @author G2rain Generator
 */
@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "MCP 外部服务表 DTO")
public class McpExternalServiceDto extends BaseDto {

    /**
     * 机构标识
     */
    @NotNull(groups = CreateGroup.class)
    @Schema(description = "机构标识（新增必填）")
    private Long organId;

    /**
     * 服务名称
     */
    @NotBlank(groups = CreateGroup.class)
    @Size(max = 128)
    @Schema(description = "服务名称（新增必填）", maxLength = 128)
    private String name;

    /**
     * 调用基址(http/https，含上下文路径)
     */
    @NotBlank(groups = CreateGroup.class)
    @Size(max = 512)
    @Schema(description = "调用基址(http/https，含上下文路径)（新增必填）", maxLength = 512)
    private String endpoint;

    /**
     * 资源类型[EXTENSION:扩展系统, CUSTOMER:租户系统]
     */
    @NotBlank(groups = CreateGroup.class)
    @Size(max = 32)
    @Schema(description = "资源类型[EXTENSION:扩展系统, CUSTOMER:租户系统]（新增必填）", maxLength = 32)
    private String resourceType;

    /**
     * 说明
     */
    @Size(max = 500)
    @Schema(description = "说明", maxLength = 500)
    private String description;
}
