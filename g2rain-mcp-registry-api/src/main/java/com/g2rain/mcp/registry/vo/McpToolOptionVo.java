package com.g2rain.mcp.registry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 技能选择工具时的工具选项。
 * <p>
 * 只含选择所需字段，不暴露 OpenAPI 文档、入参结构、路径等实现细节。
 * </p>
 *
 * @author alpha
 * @since 2026/10/27
 */
@Setter
@Getter
@NoArgsConstructor
@Schema(description = "MCP 工具选项 VO")
public class McpToolOptionVo {

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
     * Tool说明(给LLM)
     */
    @Schema(description = "Tool说明(给LLM)")
    private String description;

    /**
     * 资源类型[NATIVE:原生系统, EXTENSION:扩展系统, CUSTOMER:租户系统]
     */
    @Schema(description = "资源类型[NATIVE:原生系统, EXTENSION:扩展系统, CUSTOMER:租户系统]")
    private String resourceType;

    /**
     * 所属服务
     */
    @Schema(description = "所属服务")
    private Long serviceId;

    /**
     * 服务名称
     */
    @Schema(description = "服务名称")
    private String serviceName;

    /**
     * HTTP方法，用于读写标记与契约分组
     */
    @Schema(description = "HTTP方法")
    private String method;
}
