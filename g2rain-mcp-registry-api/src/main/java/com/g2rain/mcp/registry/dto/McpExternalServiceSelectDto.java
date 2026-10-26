package com.g2rain.mcp.registry.dto;

import com.g2rain.common.model.BaseSelectListDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * MCP外部服务表查询入参DTO
 * 用于McpExternalServiceDao.selectList方法的条件筛选
 * 表名: mcp_external_service
 *
 * @author G2rain Generator
 */
@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "MCP 外部服务表查询入参 DTO")
public class McpExternalServiceSelectDto extends BaseSelectListDto {

    /**
     * 机构标识
     */
    @Schema(description = "机构标识")
    private Long organId;

    /**
     * 服务名称
     */
    @Schema(description = "服务名称")
    private String name;

    /**
     * 调用基址(http/https，含上下文路径)
     */
    @Schema(description = "调用基址(http/https，含上下文路径)")
    private String endpoint;

    /**
     * 资源类型[EXTENSION:扩展系统, CUSTOMER:租户系统]
     */
    @Schema(description = "资源类型[EXTENSION:扩展系统, CUSTOMER:租户系统]")
    private String resourceType;

    /**
     * 状态[ACTIVE:启用, INACTIVE:禁用]
     */
    @Schema(description = "状态[ACTIVE:启用, INACTIVE:禁用]")
    private String status;

    /**
     * 说明
     */
    @Schema(description = "说明")
    private String description;
}
