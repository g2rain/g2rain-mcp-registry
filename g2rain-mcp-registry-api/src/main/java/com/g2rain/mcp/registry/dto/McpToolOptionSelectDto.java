package com.g2rain.mcp.registry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 技能选择工具的查询条件。可见范围由服务端按资源类型强制，不接收机构参数。
 *
 * @author alpha
 * @since 2026/10/27
 */
@Setter
@Getter
@NoArgsConstructor
@Schema(description = "MCP 工具选项查询入参 DTO")
public class McpToolOptionSelectDto {

    /**
     * 资源类型[NATIVE:原生系统, EXTENSION:扩展系统, CUSTOMER:租户系统]
     */
    @Schema(description = "资源类型[NATIVE:原生系统, EXTENSION:扩展系统, CUSTOMER:租户系统]", requiredMode = Schema.RequiredMode.REQUIRED)
    private String resourceType;

    /**
     * 所属服务；为空表示该类型下全部服务
     */
    @Schema(description = "所属服务；为空表示该类型下全部服务")
    private Long serviceId;

    /**
     * 关键字，模糊匹配工具名或标题
     */
    @Schema(description = "关键字，模糊匹配工具名或标题")
    private String keyword;
}
