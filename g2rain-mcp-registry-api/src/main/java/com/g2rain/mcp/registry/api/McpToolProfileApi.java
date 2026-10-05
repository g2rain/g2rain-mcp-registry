package com.g2rain.mcp.registry.api;

import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.common.model.Result;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import com.g2rain.mcp.registry.vo.McpToolProfileVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;


/**
 * MCP Tool暴露档案表API接口
 * 表名: mcp_tool_profile
 *
 * @author G2rain Generator
 */
@Tag(name = "MCP Tool暴露档案表", description = "MCP Tool暴露档案表相关接口")
public interface McpToolProfileApi {

    /**
     * 根据条件查询列表
     *
     * @param selectDto 查询条件 DTO
     * @return 数据列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询MCP Tool暴露档案表列表", description = "根据查询条件返回MCP Tool暴露档案表列表")
    Result<List<McpToolProfileVo>> selectList(McpToolProfileSelectDto selectDto);

    /**
     * 根据条件分页查询
     *
     * @param selectDto 查询条件DTO（包含分页参数）
     * @return 分页数据
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询MCP Tool暴露档案表列表", description = "分页查询MCP Tool暴露档案表列表")
    Result<PageData<McpToolProfileVo>> selectPage(PageSelectListDto<McpToolProfileSelectDto> selectDto);
}