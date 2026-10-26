package com.g2rain.mcp.registry.service;

import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.mcp.registry.dto.McpToolOptionSelectDto;
import com.g2rain.mcp.registry.dto.McpToolProfileDto;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import com.g2rain.mcp.registry.dto.UpdateStatusDto;
import com.g2rain.mcp.registry.vo.McpToolMetaVo;
import com.g2rain.mcp.registry.vo.McpToolOptionVo;
import com.g2rain.mcp.registry.vo.McpToolProfileVo;

import java.util.List;

/**
 * MCP Tool暴露档案表服务接口
 * 表名: mcp_tool_profile
 *
 * @author G2rain Generator
 */
public interface McpToolProfileService {

    /**
     * 根据条件查询列表
     *
     * @param selectDto 查询条件 DTO
     * @return VO 对象列表
     */
    List<McpToolProfileVo> selectList(McpToolProfileSelectDto selectDto);

    /**
     * 根据条件分页查询
     *
     * @param selectDto 查询条件DTO（包含分页参数）
     * @return 分页 VO 数据
     */
    PageData<McpToolProfileVo> selectPage(PageSelectListDto<McpToolProfileSelectDto> selectDto);

    /**
     * 更新工具的标题、说明、OpenAPI 文档与 inputSchema。工具一律由同步 / 导入生成，不支持单条新增；
     * 名称、类型、服务、方法、路径不可改。
     *
     * @param dto 数据传输对象，须带 id
     * @return 工具主键
     */
    Long save(McpToolProfileDto dto);

    /**
     * 技能选择工具：分页查询可选工具。NATIVE、EXTENSION 全局可见，CUSTOMER 限当前机构；只返回启用的。
     *
     * @param selectDto 类型、服务、关键字与分页
     * @return 工具选项
     */
    PageData<McpToolOptionVo> selectOptionPage(PageSelectListDto<McpToolOptionSelectDto> selectDto);

    /**
     * 技能已绑定工具：按名字批量取元数据，可见范围同 {@link #selectOptionPage}。
     *
     * @param names 工具名，单次最多 200 个
     * @return 工具元数据；不可见或不存在的名字不返回
     */
    List<McpToolMetaVo> selectMetas(List<String> names);

    /**
     * 只改启用状态。
     *
     * @param id  工具主键
     * @param dto 目标状态，ACTIVE 或 INACTIVE
     * @return 影响行数
     */
    int updateStatus(Long id, UpdateStatusDto dto);

    /**
     * 根据 ID 删除数据
     *
     * @param id 主键 ID
     * @return 操作结果（影响行数）
     */
    int delete(Long id);
}