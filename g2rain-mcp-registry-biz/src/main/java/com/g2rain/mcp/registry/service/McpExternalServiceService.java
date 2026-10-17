package com.g2rain.mcp.registry.service;

import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.mcp.registry.dto.McpExternalServiceDto;
import com.g2rain.mcp.registry.dto.McpExternalServiceSelectDto;
import com.g2rain.mcp.registry.dto.UpdateStatusDto;
import com.g2rain.mcp.registry.vo.McpExternalServiceVo;

import java.util.List;

/**
 * MCP 外部服务表服务接口
 * 表名: mcp_external_service
 *
 * @author G2rain Generator
 */
public interface McpExternalServiceService {

    /**
     * 根据条件查询列表
     *
     * @param selectDto 查询条件 DTO
     * @return VO 对象列表
     */
    List<McpExternalServiceVo> selectList(McpExternalServiceSelectDto selectDto);

    /**
     * 根据条件分页查询
     *
     * @param selectDto 查询条件DTO（包含分页参数）
     * @return 分页 VO 数据
     */
    PageData<McpExternalServiceVo> selectPage(PageSelectListDto<McpExternalServiceSelectDto> selectDto);

    /**
     * 新增或更新数据
     *
     * @param dto 数据传输对象
     * @return 操作结果（影响行数）
     */
    Long save(McpExternalServiceDto dto);

    /**
     * 根据 ID 删除数据
     *
     * @param id 主键 ID
     * @return 操作结果（影响行数）
     */
    int delete(Long id);

    /**
     * 修改状态（仅允许改 status）。
     *
     * @param id  主键
     * @param dto 目标状态
     * @return 影响行数
     */
    int updateStatus(Long id, UpdateStatusDto dto);
}
