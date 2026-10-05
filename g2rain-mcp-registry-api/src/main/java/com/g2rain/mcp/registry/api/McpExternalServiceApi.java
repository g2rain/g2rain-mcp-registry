package com.g2rain.mcp.registry.api;

import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.common.model.Result;
import com.g2rain.mcp.registry.dto.McpExternalServiceSelectDto;
import com.g2rain.mcp.registry.vo.McpExternalServiceVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;


/**
 * MCP外部服务表API接口
 * 表名: mcp_external_service
 *
 * @author G2rain Generator
 */
@Tag(name = "MCP 外部服务表", description = "MCP 外部服务表相关接口")
public interface McpExternalServiceApi {

    /**
     * 根据条件查询列表
     *
     * @param selectDto 查询条件 DTO
     * @return 数据列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询 MCP 外部服务表列表", description = "根据查询条件返回 MCP 外部服务表列表")
    Result<List<McpExternalServiceVo>> selectList(McpExternalServiceSelectDto selectDto);

    /**
     * 根据条件分页查询
     *
     * @param selectDto 查询条件DTO（包含分页参数）
     * @return 分页数据
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询 MCP 外部服务表列表", description = "分页查询 MCP 外部服务表列表")
    Result<PageData<McpExternalServiceVo>> selectPage(PageSelectListDto<McpExternalServiceSelectDto> selectDto);
}
