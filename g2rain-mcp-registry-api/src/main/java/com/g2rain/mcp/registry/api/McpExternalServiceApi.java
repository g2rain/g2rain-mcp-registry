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
     * 查询扩展系统列表（服务端写死 resourceType=EXTENSION）。
     */
    @GetMapping("/extension/list")
    @Operation(summary = "查询扩展系统列表", description = "resourceType 由服务端固定为 EXTENSION，客户端传值无效")
    Result<List<McpExternalServiceVo>> selectExtensionList(McpExternalServiceSelectDto selectDto);

    /**
     * 分页查询扩展系统（服务端写死 resourceType=EXTENSION）。
     */
    @GetMapping("/extension/page")
    @Operation(summary = "分页查询扩展系统", description = "resourceType 由服务端固定为 EXTENSION，客户端传值无效")
    Result<PageData<McpExternalServiceVo>> selectExtensionPage(PageSelectListDto<McpExternalServiceSelectDto> selectDto);

    /**
     * 查询客户系统列表（服务端写死 resourceType=CUSTOMER）。
     */
    @GetMapping("/customer/list")
    @Operation(summary = "查询客户系统列表", description = "resourceType 由服务端固定为 CUSTOMER，客户端传值无效")
    Result<List<McpExternalServiceVo>> selectCustomerList(McpExternalServiceSelectDto selectDto);

    /**
     * 分页查询客户系统（服务端写死 resourceType=CUSTOMER）。
     */
    @GetMapping("/customer/page")
    @Operation(summary = "分页查询客户系统", description = "resourceType 由服务端固定为 CUSTOMER，客户端传值无效")
    Result<PageData<McpExternalServiceVo>> selectCustomerPage(PageSelectListDto<McpExternalServiceSelectDto> selectDto);
}
