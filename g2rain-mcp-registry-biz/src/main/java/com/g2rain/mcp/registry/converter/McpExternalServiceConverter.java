package com.g2rain.mcp.registry.converter;

import com.g2rain.common.converter.CommonConverter;
import com.g2rain.mcp.registry.dao.po.McpExternalServicePo;
import com.g2rain.mcp.registry.dto.McpExternalServiceDto;
import com.g2rain.mcp.registry.vo.McpExternalServiceVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

/**
 * MCP外部服务表转换器
 * 用于Po、Vo、Dto之间的相互转换
 * 表名: mcp_external_service
 *
 * @author G2rain Generator
 */
@Mapper(uses = CommonConverter.class)
public interface McpExternalServiceConverter {

    /**
     * 单例实例，通过 {@link Mappers#getMapper(Class)} 获取 MapStruct 自动生成的实现。
     */
    McpExternalServiceConverter INSTANCE = Mappers.getMapper(McpExternalServiceConverter.class);

    /**
     * Po -> Vo
     * 自动将 createTime 和 updateTime 从 {@link LocalDateTime} 转换为 {@link String}
     */
    @Mapping(target = "createTime", source = "createTime", qualifiedByName = "localDateTimeToString")
    @Mapping(target = "updateTime", source = "updateTime", qualifiedByName = "localDateTimeToString")
    McpExternalServiceVo po2vo(McpExternalServicePo po);

    /**
     * Dto -> Po
     * 自动将 createTime 和 updateTime 从 {@link String} 转换为 {@link LocalDateTime}
     * 忽略 version 字段
     * 忽略 deleteFlag 字段
     */
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleteFlag", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createTime", source = "createTime", qualifiedByName = "stringToLocalDateTime")
    @Mapping(target = "updateTime", source = "updateTime", qualifiedByName = "stringToLocalDateTime")
    McpExternalServicePo dto2po(McpExternalServiceDto dto);
}
