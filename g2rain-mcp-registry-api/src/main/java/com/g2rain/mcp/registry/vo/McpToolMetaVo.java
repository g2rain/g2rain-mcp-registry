package com.g2rain.mcp.registry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 技能已绑定工具的元数据：在工具选项基础上增加契约骨架所需的入参结构。
 *
 * @author alpha
 * @since 2026/10/27
 */
@Setter
@Getter
@NoArgsConstructor
@Schema(description = "MCP 工具元数据 VO")
public class McpToolMetaVo extends McpToolOptionVo {

    /**
     * MCP inputSchema，用于生成契约参数骨架
     */
    @Schema(description = "MCP inputSchema")
    private String inputSchema;
}
