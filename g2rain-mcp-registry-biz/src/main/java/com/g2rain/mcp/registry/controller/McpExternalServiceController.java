package com.g2rain.mcp.registry.controller;

import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.common.model.Result;
import com.g2rain.mcp.registry.api.McpExternalServiceApi;
import com.g2rain.mcp.registry.dto.McpExternalServiceDto;
import com.g2rain.mcp.registry.dto.McpExternalServiceSelectDto;
import com.g2rain.mcp.registry.service.McpExternalServiceService;
import com.g2rain.mcp.registry.vo.McpExternalServiceVo;
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
 * MCP外部服务表控制器
 * 表名: mcp_external_service
 *
 * @author G2rain Generator
 */
@RestController
@RequestMapping("/mcp_external_service")
public class McpExternalServiceController implements McpExternalServiceApi {

    @Resource(name = "mcpExternalServiceServiceImpl")
    private McpExternalServiceService mcpExternalServiceService;

    @Override
    public Result<List<McpExternalServiceVo>> selectList(McpExternalServiceSelectDto selectDto) {
        return Result.success(mcpExternalServiceService.selectList(selectDto));
    }

    @Override
    public Result<PageData<McpExternalServiceVo>> selectPage(PageSelectListDto<McpExternalServiceSelectDto> selectDto) {
        return Result.successPage(mcpExternalServiceService.selectPage(selectDto));
    }

    @PostMapping("/save")
    @Operation(summary = "新增或更新 MCP 外部服务表信息", description = "新增或更新 MCP 外部服务表基础信息")
    public Result<Long> save(@RequestBody McpExternalServiceDto dto) {
        return Result.success(mcpExternalServiceService.save(dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 MCP 外部服务表记录", description = "根据主键删除 MCP 外部服务表记录")
    public Result<Integer> delete(@Parameter(description = "MCP 外部服务表标识") @PathVariable Long id) {
        return Result.success(mcpExternalServiceService.delete(id));
    }
}
