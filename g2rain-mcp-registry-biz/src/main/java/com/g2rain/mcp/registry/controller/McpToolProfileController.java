package com.g2rain.mcp.registry.controller;

import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.common.model.Result;
import com.g2rain.mcp.registry.api.McpToolProfileApi;
import com.g2rain.mcp.registry.dto.McpToolOptionSelectDto;
import com.g2rain.mcp.registry.dto.McpToolProfileDto;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import com.g2rain.mcp.registry.dto.NativeSyncDto;
import com.g2rain.mcp.registry.dto.OpenApiConfirmDto;
import com.g2rain.mcp.registry.dto.OpenApiPreviewDto;
import com.g2rain.mcp.registry.dto.UpdateStatusDto;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.openapi.ConvertedOperation;
import com.g2rain.mcp.registry.service.McpToolProfileService;
import com.g2rain.mcp.registry.service.impl.McpToolImportServiceImpl;
import com.g2rain.mcp.registry.vo.McpToolImportResultVo;
import com.g2rain.mcp.registry.vo.McpToolMetaVo;
import com.g2rain.mcp.registry.vo.McpToolOptionVo;
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
    public Result<List<McpToolProfileVo>> selectNativeList(McpToolProfileSelectDto selectDto) {
        selectDto.setResourceType(ResourceType.NATIVE.name());
        return Result.success(mcpToolProfileService.selectList(selectDto));
    }

    @Override
    public Result<PageData<McpToolProfileVo>> selectNativePage(PageSelectListDto<McpToolProfileSelectDto> selectDto) {
        selectDto.getQuery().setResourceType(ResourceType.NATIVE.name());
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

    @PostMapping("/extension/save")
    @IdentityInject(organIdPropertyName = "organId")
    @Operation(summary = "更新扩展系统工具", description = "只改标题、说明、OpenAPI 文档与 inputSchema；工具由导入生成，不支持单条新增")
    public Result<Long> saveExtension(@RequestBody McpToolProfileDto dto) {
        dto.setResourceType(ResourceType.EXTENSION.name());
        return Result.success(mcpToolProfileService.save(dto));
    }

    @PostMapping("/customer/save")
    @IdentityInject(organIdPropertyName = "organId")
    @Operation(summary = "更新租户系统工具", description = "只改标题、说明、OpenAPI 文档与 inputSchema；工具由导入生成，不支持单条新增")
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
    @Operation(summary = "确认导入扩展或租户工具", description = "只写入勾选的接口；工具名由服务端生成")
    public Result<McpToolImportResultVo> confirmImport(@RequestBody OpenApiConfirmDto dto) {
        return Result.success(mcpToolImportService.confirm(dto));
    }

    @PostMapping("/sync_native")
    @Operation(summary = "同步原生工具", description = "按服务导入选中或全部资源接口")
    public Result<McpToolImportResultVo> syncNative(@RequestBody NativeSyncDto dto) {
        return Result.success(mcpToolImportService.syncNative(dto));
    }

    @Override
    public Result<PageData<McpToolOptionVo>> selectOptionPage(PageSelectListDto<McpToolOptionSelectDto> selectDto) {
        return Result.successPage(mcpToolProfileService.selectOptionPage(selectDto));
    }

    @PostMapping("/options/by_names")
    @Operation(summary = "按名字批量查询工具元数据", description = "技能已绑定工具；可见范围由服务端强制，单次最多 200 个")
    public Result<List<McpToolMetaVo>> selectMetas(@RequestBody List<String> names) {
        return Result.success(mcpToolProfileService.selectMetas(names));
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
