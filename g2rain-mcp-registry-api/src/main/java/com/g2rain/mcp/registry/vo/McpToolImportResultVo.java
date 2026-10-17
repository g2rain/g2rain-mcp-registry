package com.g2rain.mcp.registry.vo;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 一次导入的写入结果。
 *
 * @param created 新增条数
 * @param updated 更新条数
 * @param skipped 跳过条数（无文档、转换失败或未选中）
 * @author alpha
 * @since 2026/10/16
 */
@Getter
@Schema(description = "工具导入结果")
public class McpToolImportResultVo {

    @Schema(description = "新增条数")
    private final int created;

    @Schema(description = "更新条数")
    private final int updated;

    @Schema(description = "跳过条数")
    private final int skipped;

    public McpToolImportResultVo(int created, int updated, int skipped) {
        this.created = created;
        this.updated = updated;
        this.skipped = skipped;
    }
}
