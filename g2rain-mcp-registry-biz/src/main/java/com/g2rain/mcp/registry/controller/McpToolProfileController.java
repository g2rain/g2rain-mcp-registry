package com.g2rain.mcp.registry.controller;

import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.common.model.Result;
import com.g2rain.mcp.registry.api.McpToolProfileApi;
import com.g2rain.mcp.registry.dto.McpToolProfileDto;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import com.g2rain.mcp.registry.service.McpToolProfileService;
import com.g2rain.mcp.registry.vo.McpToolProfileVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP Tool暴露档案表控制器
 * 表名: mcp_tool_profile
 *
 * @author G2rain Generator
 */
@RestController
@RequestMapping("/mcp_tool_profile")
public class McpToolProfileController implements McpToolProfileApi {

    @Resource(name = "mcpToolProfileServiceImpl")
    private McpToolProfileService mcpToolProfileService;

    @Override
    public Result<List<McpToolProfileVo>> selectList(McpToolProfileSelectDto selectDto) {
        return Result.success(mcpToolProfileService.selectList(selectDto));
    }

    @Override
    public Result<PageData<McpToolProfileVo>> selectPage(PageSelectListDto<McpToolProfileSelectDto> selectDto) {
        return Result.successPage(mcpToolProfileService.selectPage(selectDto));
    }

    @PostMapping("/save")
    @Operation(summary = "新增或更新MCP Tool暴露档案表信息", description = "新增或更新MCP Tool暴露档案表基础信息")
    public Result<Long> save(@RequestBody McpToolProfileDto dto) {
        return Result.success(mcpToolProfileService.save(dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除MCP Tool暴露档案表记录", description = "根据主键删除MCP Tool暴露档案表记录")
    public Result<Integer> delete(@Parameter(description = "MCP Tool暴露档案表标识") @PathVariable Long id) {
        return Result.success(mcpToolProfileService.delete(id));
    }
}