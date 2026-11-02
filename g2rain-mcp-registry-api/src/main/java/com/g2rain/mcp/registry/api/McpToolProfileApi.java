package com.g2rain.mcp.registry.api;

import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.common.model.Result;
import com.g2rain.mcp.registry.dto.McpToolOptionSelectDto;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import com.g2rain.mcp.registry.vo.McpToolOptionVo;
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

    @GetMapping("/native/list")
    @Operation(summary = "查询原生工具列表", description = "resourceType 由服务端固定为 NATIVE")
    Result<List<McpToolProfileVo>> selectNativeList(McpToolProfileSelectDto selectDto);

    @GetMapping("/native/page")
    @Operation(summary = "分页查询原生工具", description = "resourceType 由服务端固定为 NATIVE")
    Result<PageData<McpToolProfileVo>> selectNativePage(PageSelectListDto<McpToolProfileSelectDto> selectDto);

    @GetMapping("/extension/list")
    @Operation(summary = "查询扩展系统工具列表", description = "resourceType 由服务端固定为 EXTENSION")
    Result<List<McpToolProfileVo>> selectExtensionList(McpToolProfileSelectDto selectDto);

    @GetMapping("/extension/page")
    @Operation(summary = "分页查询扩展系统工具", description = "resourceType 由服务端固定为 EXTENSION")
    Result<PageData<McpToolProfileVo>> selectExtensionPage(PageSelectListDto<McpToolProfileSelectDto> selectDto);

    @GetMapping("/customer/list")
    @Operation(summary = "查询租户系统工具列表", description = "resourceType 由服务端固定为 CUSTOMER")
    Result<List<McpToolProfileVo>> selectCustomerList(McpToolProfileSelectDto selectDto);

    @GetMapping("/customer/page")
    @Operation(summary = "分页查询租户系统工具", description = "resourceType 由服务端固定为 CUSTOMER")
    Result<PageData<McpToolProfileVo>> selectCustomerPage(PageSelectListDto<McpToolProfileSelectDto> selectDto);

    /**
     * 技能选择工具：NATIVE、EXTENSION 全局可见，CUSTOMER 限当前机构；只返回启用的工具。
     */
    @GetMapping("/options/page")
    @Operation(summary = "分页查询可选工具", description = "可见范围由服务端按资源类型强制，只返回选择所需字段")
    Result<PageData<McpToolOptionVo>> selectOptionPage(PageSelectListDto<McpToolOptionSelectDto> selectDto);
}
