package com.g2rain.mcp.registry.dao;

import com.g2rain.data.isolation.annotations.DataIsolation;
import com.g2rain.data.isolation.annotations.IgnoreIsolation;
import com.g2rain.mcp.registry.dao.po.McpToolProfilePo;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
     * 名称冲突计数。
     * <p>
     * 共享工具（BUILT_IN / EXTENSION）：与任意未删除同名冲突。
     * CUSTOMER：与共享工具及本机构 CUSTOMER 同名冲突。
     * </p>
     *
     * @param name         工具名
     * @param resourceType 待写入的资源类型
     * @param organId      CUSTOMER 时本机构；共享类型可空
     * @param excludeId    更新时排除自身，可空
     */
    @IgnoreIsolation
    int countNameConflict(@Param("name") String name,
                          @Param("resourceType") String resourceType,
                          @Param("organId") Long organId,
                          @Param("excludeId") Long excludeId);

    /**
     * 冲突范围内已占用的工具名（导入批量校验用）。
     *
     * @param resourceType 待写入的资源类型
     * @param organId      CUSTOMER 时本机构
     */
    @IgnoreIsolation
    List<String> selectConflictNames(@Param("resourceType") String resourceType,
                                     @Param("organId") Long organId);

    /**
     * 按服务编码 + 机构软删工具（级联删外部服务用）。
     */
    @IgnoreIsolation
    int deleteByServiceCodeAndOrganId(@Param("serviceCode") String serviceCode,
                                      @Param("organId") Long organId);
}
