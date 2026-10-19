package com.g2rain.mcp.registry.controller;

import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.common.model.Result;
import com.g2rain.mcp.registry.api.McpToolProfileApi;
import com.g2rain.mcp.registry.dto.BuiltInSyncDto;
import com.g2rain.mcp.registry.dto.McpToolProfileDto;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import com.g2rain.mcp.registry.dto.OpenApiConfirmDto;
import com.g2rain.mcp.registry.dto.OpenApiPreviewDto;
import com.g2rain.mcp.registry.dto.UpdateStatusDto;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.openapi.ConvertedOperation;
import com.g2rain.mcp.registry.service.McpToolProfileService;
import com.g2rain.mcp.registry.service.impl.McpToolImportServiceImpl;
import com.g2rain.mcp.registry.vo.McpToolImportResultVo;
import com.g2rain.mcp.registry.vo.McpToolProfileVo;
import com.g2rain.web.interceptors.annotations.IdentityInject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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

    @Resource
    private McpToolImportServiceImpl mcpToolImportService;

    @Override
    public Result<List<McpToolProfileVo>> selectBuiltInList(McpToolProfileSelectDto selectDto) {
        selectDto.setResourceType(ResourceType.BUILT_IN.name());
        return Result.success(mcpToolProfileService.selectList(selectDto));
    }

    @Override
    public Result<PageData<McpToolProfileVo>> selectBuiltInPage(PageSelectListDto<McpToolProfileSelectDto> selectDto) {
        selectDto.getQuery().setResourceType(ResourceType.BUILT_IN.name());
        return Result.successPage(mcpToolProfileService.selectPage(selectDto));
    }

    @Override
    public Result<List<McpToolProfileVo>> selectExtensionList(McpToolProfileSelectDto selectDto) {
        selectDto.setResourceType(ResourceType.EXTENSION.name());
        return Result.success(mcpToolProfileService.selectList(selectDto));
    }

    @Override
    public Result<PageData<McpToolProfileVo>> selectExtensionPage(PageSelectListDto<McpToolProfileSelectDto> selectDto) {
        selectDto.getQuery().setResourceType(ResourceType.EXTENSION.name());
        return Result.successPage(mcpToolProfileService.selectPage(selectDto));
    }

    @Override
    public Result<List<McpToolProfileVo>> selectCustomerList(McpToolProfileSelectDto selectDto) {
        selectDto.setResourceType(ResourceType.CUSTOMER.name());
        return Result.success(mcpToolProfileService.selectList(selectDto));
    }

    @Override
    public Result<PageData<McpToolProfileVo>> selectCustomerPage(PageSelectListDto<McpToolProfileSelectDto> selectDto) {
        selectDto.getQuery().setResourceType(ResourceType.CUSTOMER.name());
        return Result.successPage(mcpToolProfileService.selectPage(selectDto));
    }

    @PostMapping("/built_in/save")
    @IdentityInject(organIdPropertyName = "organId")
    @Operation(summary = "新增或更新自有系统工具", description = "resourceType 由服务端固定为 BUILT_IN")
    public Result<Long> saveBuiltIn(@RequestBody McpToolProfileDto dto) {
        dto.setResourceType(ResourceType.BUILT_IN.name());
        return Result.success(mcpToolProfileService.save(dto));
    }

    @PostMapping("/extension/save")
    @IdentityInject(organIdPropertyName = "organId")
    @Operation(summary = "新增或更新扩展系统工具", description = "resourceType 由服务端固定为 EXTENSION")
    public Result<Long> saveExtension(@RequestBody McpToolProfileDto dto) {
        dto.setResourceType(ResourceType.EXTENSION.name());
        return Result.success(mcpToolProfileService.save(dto));
    }

    @PostMapping("/customer/save")
    @IdentityInject(organIdPropertyName = "organId")
    @Operation(summary = "新增或更新客户系统工具", description = "resourceType 由服务端固定为 CUSTOMER")
    public Result<Long> saveCustomer(@RequestBody McpToolProfileDto dto) {
        dto.setResourceType(ResourceType.CUSTOMER.name());
        return Result.success(mcpToolProfileService.save(dto));
    }

    @PostMapping("/preview")
    @Operation(summary = "预览 OpenAPI 文档", description = "按接口展开，不写库")
    public Result<List<ConvertedOperation>> preview(@RequestBody OpenApiPreviewDto dto) {
        return Result.success(mcpToolImportService.preview(dto));
    }

    @PostMapping("/confirm_import")
    @Operation(summary = "确认导入扩展或客户工具", description = "只写入勾选的接口")
    public Result<McpToolImportResultVo> confirmImport(@RequestBody OpenApiConfirmDto dto) {
        return Result.success(mcpToolImportService.confirm(dto));
    }

    @PostMapping("/sync_built_in")
    @Operation(summary = "同步自有系统工具", description = "按服务导入选中或全部资源接口")
    public Result<McpToolImportResultVo> syncBuiltIn(@RequestBody BuiltInSyncDto dto) {
        return Result.success(mcpToolImportService.syncBuiltIn(dto));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "修改工具状态", description = "仅允许改状态")
    public Result<Integer> updateStatus(@Parameter(description = "工具标识") @PathVariable Long id,
                                        @RequestBody @Valid UpdateStatusDto dto) {
        return Result.success(mcpToolProfileService.updateStatus(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除MCP Tool暴露档案表记录", description = "根据主键删除MCP Tool暴露档案表记录")
    public Result<Integer> delete(@Parameter(description = "MCP Tool暴露档案表标识") @PathVariable Long id) {
        return Result.success(mcpToolProfileService.delete(id));
    }
}
