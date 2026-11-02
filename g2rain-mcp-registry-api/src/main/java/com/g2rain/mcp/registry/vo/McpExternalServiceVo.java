package com.g2rain.mcp.registry.vo;

import com.g2rain.common.json.AdminCompanyCondition;
import com.g2rain.common.json.ConditionalJsonIgnore;
import com.g2rain.common.model.BaseVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MCP外部服务表返回VO
 * 关联表名: mcp_external_service
 * 功能：封装接口返回数据，继承BaseVo复用基础字段逻辑，隔离数据库实体与前端展示层
 *
 * @author G2rain Generator
 */
@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "MCP 外部服务表 VO")
public class McpExternalServiceVo extends BaseVo {

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

    /**
     * 删除标识[0:未删除, 1:已删除]
     */
    @Schema(description = "删除标识（0 未删除，1 已删除）", example = "false")
    @ConditionalJsonIgnore(adminCompany = AdminCompanyCondition.TRUE)
    private Boolean deleteFlag;
}
