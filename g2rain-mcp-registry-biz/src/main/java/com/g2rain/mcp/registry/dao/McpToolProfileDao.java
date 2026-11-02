package com.g2rain.mcp.registry.dao;

import com.g2rain.data.isolation.annotations.DataIsolation;
import com.g2rain.data.isolation.annotations.IgnoreIsolation;
import com.g2rain.mcp.registry.dao.po.McpToolProfilePo;
import com.g2rain.mcp.registry.dto.McpToolOptionSelectDto;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * MCP Tool暴露档案表数据访问接口
 * 表名: mcp_tool_profile
 *
 * @author G2rain Generator
 */
@Mapper
@DataIsolation(organIdPropertyName = "organId", organIdColumnName = "organ_id")
public interface McpToolProfileDao {

    /**
     * 插入单条记录
     *
     * @param entity 实体对象
     * @return 影响行数
     */
    int insert(McpToolProfilePo entity);

    /**
     * 批量插入记录
     *
     * @param list 实体对象列表
     * @return 影响行数
     */
    int insertMultiple(List<McpToolProfilePo> list);

    /**
     * 根据 ID 更新记录
     *
     * @param entity 实体对象
     * @return 影响行数
     */
    int update(McpToolProfilePo entity);

    /**
     * 根据 ID 删除记录
     *
     * @param id 主键 ID
     * @return 影响行数
     */
    int delete(Long id);

    /**
     * 根据ID和Version更新记录（乐观锁更新）
     *
     * @param entity 实体对象（必须包含version字段）
     * @return 影响行数
     */
    int updateByVersion(McpToolProfilePo entity);

    /**
     * 根据 ID 查询记录
     *
     * @param id 主键 ID
     * @return 实体对象
     */
    McpToolProfilePo selectById(Long id);

    /**
     * 根据查询入参 DTO 筛选列表
     *
     * @param selectDto 查询条件 DTO
     * @return 实体对象列表
     */
    List<McpToolProfilePo> selectList(McpToolProfileSelectDto selectDto);

    /**
     * 插入单条记录（忽略数据隔离）
     *
     * @param entity 实体对象
     * @return 影响行数
     */
    @IgnoreIsolation
    int insertWithoutIsolation(McpToolProfilePo entity);

    /**
     * 根据ID更新记录（忽略数据隔离）
     *
     * @param entity 实体对象
     * @return 影响行数
     */
    @IgnoreIsolation
    int updateWithoutIsolation(McpToolProfilePo entity);

    /**
     * 根据ID查询记录（忽略数据隔离）
     *
     * @param id 主键 ID
     * @return 实体对象
     */
    @IgnoreIsolation
    McpToolProfilePo selectByIdWithoutIsolation(Long id);

    /**
     * 根据查询入参DTO筛选列表（忽略数据隔离）
     *
     * @param selectDto 查询条件 DTO
     * @return 实体对象列表
     */
    @IgnoreIsolation
    List<McpToolProfilePo> selectListWithoutIsolation(McpToolProfileSelectDto selectDto);

    /**
     * 同一机构 + 同一资源类型下已占用的工具名（只取 id、name、service_id、method、path），导入时判重。
     *
     * @param resourceType 资源类型
     * @param organId      机构
     * @return 已占用工具
     */
    @IgnoreIsolation
    List<McpToolProfilePo> selectNameOwners(@Param("resourceType") String resourceType,
                                           @Param("organId") Long organId);

    /**
     * 按服务删除工具（服务删除时级联）。
     *
     * @param serviceId 服务
     * @return 删除条数
     */
    @IgnoreIsolation
    int deleteByServiceId(@Param("serviceId") Long serviceId);

    /**
     * 技能选择工具：NATIVE、EXTENSION 全局，CUSTOMER 限 {@code organId}；只取启用的，只查选择所需列。
     *
     * @param query   类型、服务、关键字
     * @param organId 当前机构
     * @return 工具选项
     */
    @IgnoreIsolation
    List<McpToolProfilePo> selectOptions(@Param("query") McpToolOptionSelectDto query,
                                         @Param("organId") Long organId);

    /**
     * 技能已绑定工具：按名字批量取元数据，可见范围同 {@link #selectOptions}。
     *
     * @param names   工具名
     * @param organId 当前机构
     * @return 工具元数据
     */
    @IgnoreIsolation
    List<McpToolProfilePo> selectMetas(@Param("names") Collection<String> names,
                                       @Param("organId") Long organId);
}
