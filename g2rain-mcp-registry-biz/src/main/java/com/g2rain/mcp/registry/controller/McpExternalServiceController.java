package com.g2rain.mcp.registry.controller;

import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.common.model.Result;
import com.g2rain.mcp.registry.api.McpExternalServiceApi;
import com.g2rain.mcp.registry.dto.McpExternalServiceDto;
import com.g2rain.mcp.registry.dto.McpExternalServiceSelectDto;
import com.g2rain.mcp.registry.dto.UpdateStatusDto;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.service.McpExternalServiceService;
import com.g2rain.mcp.registry.vo.McpExternalServiceVo;
import com.g2rain.web.interceptors.annotations.IdentityInject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
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
    public Result<List<McpExternalServiceVo>> selectExtensionList(McpExternalServiceSelectDto selectDto) {
        selectDto.setResourceType(ResourceType.EXTENSION.name());
        return Result.success(mcpExternalServiceService.selectList(selectDto));
    }

    @Override
    public Result<PageData<McpExternalServiceVo>> selectExtensionPage(PageSelectListDto<McpExternalServiceSelectDto> selectDto) {
        selectDto.getQuery().setResourceType(ResourceType.EXTENSION.name());
        return Result.successPage(mcpExternalServiceService.selectPage(selectDto));
    }

    @Override
    public Result<List<McpExternalServiceVo>> selectCustomerList(McpExternalServiceSelectDto selectDto) {
        selectDto.setResourceType(ResourceType.CUSTOMER.name());
        return Result.success(mcpExternalServiceService.selectList(selectDto));
    }

    @Override
    public Result<PageData<McpExternalServiceVo>> selectCustomerPage(PageSelectListDto<McpExternalServiceSelectDto> selectDto) {
        selectDto.getQuery().setResourceType(ResourceType.CUSTOMER.name());
        return Result.successPage(mcpExternalServiceService.selectPage(selectDto));
    }

    @PostMapping("/extension/save")
    @IdentityInject(organIdPropertyName = "organId")
    @Operation(summary = "新增或更新扩展系统", description = "resourceType 由服务端固定为 EXTENSION")
    public Result<Long> saveExtension(@RequestBody McpExternalServiceDto dto) {
        dto.setResourceType(ResourceType.EXTENSION.name());
        return Result.success(mcpExternalServiceService.save(dto));
    }

    @PostMapping("/customer/save")
    @IdentityInject(organIdPropertyName = "organId")
    @Operation(summary = "新增或更新客户系统", description = "resourceType 由服务端固定为 CUSTOMER")
    public Result<Long> saveCustomer(@RequestBody McpExternalServiceDto dto) {
        dto.setResourceType(ResourceType.CUSTOMER.name());
        return Result.success(mcpExternalServiceService.save(dto));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "修改外部服务状态", description = "仅允许改状态")
    public Result<Integer> updateStatus(@Parameter(description = "外部服务标识") @PathVariable Long id, @RequestBody @Validated UpdateStatusDto dto) {
        return Result.success(mcpExternalServiceService.updateStatus(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 MCP 外部服务表记录", description = "根据主键删除 MCP 外部服务表记录")
    public Result<Integer> delete(@Parameter(description = "MCP 外部服务表标识") @PathVariable Long id) {
        return Result.success(mcpExternalServiceService.delete(id));
    }
}
