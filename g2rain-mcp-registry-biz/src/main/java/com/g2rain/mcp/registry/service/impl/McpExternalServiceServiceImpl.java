package com.g2rain.mcp.registry.service.impl;

import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.id.IdGenerator;
import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Moments;
import com.g2rain.common.validation.Validations;
import com.g2rain.mcp.registry.converter.McpExternalServiceConverter;
import com.g2rain.mcp.registry.dao.McpExternalServiceDao;
import com.g2rain.mcp.registry.dao.po.McpExternalServicePo;
import com.g2rain.mcp.registry.dto.McpExternalServiceDto;
import com.g2rain.mcp.registry.dto.McpExternalServiceSelectDto;
import com.g2rain.mcp.registry.service.McpExternalServiceService;
import com.g2rain.mcp.registry.vo.McpExternalServiceVo;
import com.g2rain.mybatis.pagination.PageContext;
import com.g2rain.mybatis.pagination.model.Page;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * MCP外部服务表服务实现类
 * 表名: mcp_external_service
 *
 * @author G2rain Generator
 */
@Service(value = "mcpExternalServiceServiceImpl")
public class McpExternalServiceServiceImpl implements McpExternalServiceService {

    @Resource(name = "mcpExternalServiceDao")
    private McpExternalServiceDao mcpExternalServiceDao;

    private IdGenerator idGenerator;

    @Qualifier("idGenerator")
    @Autowired(required = false)
    public void setIdGenerator(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public List<McpExternalServiceVo> selectList(McpExternalServiceSelectDto selectDto) {
        return mcpExternalServiceDao.selectList(selectDto)
            .stream()
            .map(McpExternalServiceConverter.INSTANCE::po2vo)
            .toList();
    }

    @Override
    public PageData<McpExternalServiceVo> selectPage(PageSelectListDto<McpExternalServiceSelectDto> selectDto) {
        Page<McpExternalServicePo> page = PageContext.of(selectDto.getPageNum(), selectDto.getPageSize(), () -> {
            mcpExternalServiceDao.selectList(selectDto.getQuery());
        });
        List<McpExternalServiceVo> result = page.getResult()
            .stream()
            .map(McpExternalServiceConverter.INSTANCE::po2vo)
            .toList();
        return PageData.of(page.getPageNum(), page.getPageSize(), page.getTotal(), result);
    }

    @Override
    public Long save(McpExternalServiceDto dto) {
        Validations.validateSave(dto);

        // 转换 DTO 为 PO
        McpExternalServicePo entity = McpExternalServiceConverter.INSTANCE.dto2po(dto);

        // 判断是新增还是更新
        Long id = entity.getId();
        if (Objects.isNull(id) || id == 0) {
            // 新增：使用IdGenerator生成主键
            entity.setId(idGenerator.generateId());
            LocalDateTime now = Moments.now();
            entity.setUpdateTime(now);
            entity.setCreateTime(now);
            int success = mcpExternalServiceDao.insert(entity);
            Asserts.greaterThan(success, 0, SystemErrorCode.CREATE_DATA_ERROR);
        } else {
            McpExternalServicePo existing = mcpExternalServiceDao.selectById(id);
            Asserts.isTrue(Objects.nonNull(existing), SystemErrorCode.DATA_NOT_EXISTS, id);
            // 更新：直接更新
            entity.setUpdateTime(Moments.now());
            int success = mcpExternalServiceDao.update(entity);
            Asserts.greaterThan(success, 0, SystemErrorCode.UPDATE_DATA_ERROR, id);
        }

        return entity.getId();
    }

    @Override
    public int delete(Long id) {
        McpExternalServicePo existing = mcpExternalServiceDao.selectById(id);
        Asserts.isTrue(Objects.nonNull(existing), SystemErrorCode.DATA_NOT_EXISTS, id);
        return mcpExternalServiceDao.delete(id);
    }
}
