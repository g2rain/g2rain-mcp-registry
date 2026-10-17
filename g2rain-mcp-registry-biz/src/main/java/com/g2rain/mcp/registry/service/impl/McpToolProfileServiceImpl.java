package com.g2rain.mcp.registry.service.impl;

import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.id.IdGenerator;
import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Moments;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.validation.Validations;
import com.g2rain.common.web.PrincipalContextHolder;
import com.g2rain.mcp.registry.converter.McpToolProfileConverter;
import com.g2rain.mcp.registry.dao.McpToolProfileDao;
import com.g2rain.mcp.registry.dao.po.McpToolProfilePo;
import com.g2rain.mcp.registry.dto.McpToolProfileDto;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import com.g2rain.mcp.registry.dto.UpdateStatusDto;
import com.g2rain.mcp.registry.enums.ResourceStatus;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.runtime.BuiltInToolLoader;
import com.g2rain.mcp.registry.runtime.ExtensionToolLoader;
import com.g2rain.mcp.registry.service.McpToolNameGuard;
import com.g2rain.mcp.registry.service.McpToolProfileService;
import com.g2rain.mcp.registry.vo.McpToolProfileVo;
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
 * MCP Tool暴露档案表服务实现类
 * 表名: mcp_tool_profile
 *
 * @author G2rain Generator
 */
@Service(value = "mcpToolProfileServiceImpl")
public class McpToolProfileServiceImpl implements McpToolProfileService {

    @Resource(name = "mcpToolProfileDao")
    private McpToolProfileDao mcpToolProfileDao;

    @Resource
    private BuiltInToolLoader builtInToolLoader;

    @Resource
    private ExtensionToolLoader extensionToolLoader;

    @Resource
    private McpToolNameGuard mcpToolNameGuard;

    private IdGenerator idGenerator;

    @Qualifier("idGenerator")
    @Autowired(required = false)
    public void setIdGenerator(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public List<McpToolProfileVo> selectList(McpToolProfileSelectDto selectDto) {
        return mcpToolProfileDao.selectList(selectDto)
            .stream()
            .map(McpToolProfileConverter.INSTANCE::po2vo)
            .toList();
    }

    @Override
    public PageData<McpToolProfileVo> selectPage(PageSelectListDto<McpToolProfileSelectDto> selectDto) {
        Page<McpToolProfilePo> page = PageContext.of(selectDto.getPageNum(), selectDto.getPageSize(), () -> {
            mcpToolProfileDao.selectList(selectDto.getQuery());
        });
        List<McpToolProfileVo> result = page.getResult()
            .stream()
            .map(McpToolProfileConverter.INSTANCE::po2vo)
            .toList();
        return PageData.of(page.getPageNum(), page.getPageSize(), page.getTotal(), result);
    }

    @Override
    public Long save(McpToolProfileDto dto) {
        Long id = dto.getId();
        if (Objects.isNull(id) || id == 0) {
            if (Objects.isNull(dto.getOrganId())) {
                dto.setOrganId(PrincipalContextHolder.getOrganId());
            }
            if (Strings.isBlank(dto.getStatus())) {
                dto.setStatus(ResourceStatus.ACTIVE.name());
            }
        }
        Validations.validateSave(dto);

        // 转换 DTO 为 PO
        McpToolProfilePo entity = McpToolProfileConverter.INSTANCE.dto2po(dto);

        if (Objects.isNull(id) || id == 0) {
            mcpToolNameGuard.assertUnique(entity.getName(), entity.getResourceType(), entity.getOrganId(), null);
            // 新增：使用IdGenerator生成主键
            entity.setId(idGenerator.generateId());
            LocalDateTime now = Moments.now();
            entity.setUpdateTime(now);
            entity.setCreateTime(now);
            int success = mcpToolProfileDao.insert(entity);
            Asserts.greaterThan(success, 0, SystemErrorCode.CREATE_DATA_ERROR);
            refreshShared(entity.getResourceType(), entity.getServiceCode());
        } else {
            McpToolProfilePo existing = mcpToolProfileDao.selectById(id);
            Asserts.isTrue(Objects.nonNull(existing), SystemErrorCode.DATA_NOT_EXISTS, id);
            Asserts.isTrue(Objects.equals(existing.getResourceType(), dto.getResourceType()),
                SystemErrorCode.DATA_NOT_EXISTS, id);
            if (Strings.isNotBlank(entity.getName())) {
                Long organId = Objects.nonNull(existing.getOrganId()) ? existing.getOrganId() : entity.getOrganId();
                mcpToolNameGuard.assertUnique(entity.getName(), existing.getResourceType(), organId, id);
            }
            // 更新：类型创建后不可变
            entity.setResourceType(null);
            entity.setUpdateTime(Moments.now());
            int success = mcpToolProfileDao.update(entity);
            Asserts.greaterThan(success, 0, SystemErrorCode.UPDATE_DATA_ERROR, id);
            refreshShared(existing.getResourceType(), existing.getServiceCode());
        }

        return entity.getId();
    }

    @Override
    public int updateStatus(Long id, UpdateStatusDto dto) {
        ResourceStatus.validate(dto.getStatus());
        McpToolProfilePo existing = mcpToolProfileDao.selectById(id);
        Asserts.isTrue(Objects.nonNull(existing), SystemErrorCode.DATA_NOT_EXISTS, id);
        McpToolProfilePo entity = new McpToolProfilePo();
        entity.setId(id);
        entity.setStatus(dto.getStatus());
        entity.setUpdateTime(Moments.now());
        int success = mcpToolProfileDao.update(entity);
        Asserts.greaterThan(success, 0, SystemErrorCode.UPDATE_DATA_ERROR, id);
        refreshShared(existing.getResourceType(), existing.getServiceCode());
        return success;
    }

    @Override
    public int delete(Long id) {
        McpToolProfilePo existing = mcpToolProfileDao.selectById(id);
        Asserts.isTrue(Objects.nonNull(existing), SystemErrorCode.DATA_NOT_EXISTS, id);
        int deleted = mcpToolProfileDao.delete(id);
        refreshShared(existing.getResourceType(), existing.getServiceCode());
        return deleted;
    }

    private void refreshShared(String resourceType, String serviceCode) {
        if (ResourceType.BUILT_IN.name().equals(resourceType)) {
            builtInToolLoader.reload(serviceCode);
        } else if (ResourceType.EXTENSION.name().equals(resourceType)) {
            extensionToolLoader.reload(serviceCode);
        }
    }
}
