package com.g2rain.mcp.registry.service.impl;

import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.model.PageData;
import com.g2rain.common.model.PageSelectListDto;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Moments;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.validation.Validations;
import com.g2rain.common.web.PrincipalContextHolder;
import com.g2rain.mcp.registry.converter.McpToolProfileConverter;
import com.g2rain.mcp.registry.dao.McpToolProfileDao;
import com.g2rain.mcp.registry.dao.po.McpToolProfilePo;
import com.g2rain.mcp.registry.dto.McpToolOptionSelectDto;
import com.g2rain.mcp.registry.dto.McpToolProfileDto;
import com.g2rain.mcp.registry.dto.McpToolProfileSelectDto;
import com.g2rain.mcp.registry.dto.UpdateStatusDto;
import com.g2rain.mcp.registry.enums.ResourceStatus;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.runtime.ExtensionToolLoader;
import com.g2rain.mcp.registry.runtime.NativeToolLoader;
import com.g2rain.mcp.registry.service.McpToolProfileService;
import com.g2rain.mcp.registry.service.ServiceNameResolver;
import com.g2rain.mcp.registry.vo.McpToolMetaVo;
import com.g2rain.mcp.registry.vo.McpToolOptionVo;
import com.g2rain.mcp.registry.vo.McpToolProfileVo;
import com.g2rain.mybatis.pagination.PageContext;
import com.g2rain.mybatis.pagination.model.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP Tool暴露档案表服务实现类
 * 表名: mcp_tool_profile
 *
 * @author G2rain Generator
 */
@Service(value = "mcpToolProfileServiceImpl")
public class McpToolProfileServiceImpl implements McpToolProfileService {

    /**
     * 按名字批量查询的单次上限。
     */
    private static final int MAX_NAMES = 200;

    @Resource(name = "mcpToolProfileDao")
    private McpToolProfileDao mcpToolProfileDao;

    @Resource
    private NativeToolLoader nativeToolLoader;

    @Resource
    private ExtensionToolLoader extensionToolLoader;

    @Resource
    private ServiceNameResolver serviceNameResolver;

    @Override
    public List<McpToolProfileVo> selectList(McpToolProfileSelectDto selectDto) {
        List<McpToolProfileVo> result = mcpToolProfileDao.selectList(selectDto)
            .stream()
            .map(McpToolProfileConverter.INSTANCE::po2vo)
            .toList();
        fillProfileServiceNames(result);
        return result;
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
        fillProfileServiceNames(result);
        return PageData.of(page.getPageNum(), page.getPageSize(), page.getTotal(), result);
    }

    @Override
    public Long save(McpToolProfileDto dto) {
        Long id = dto.getId();
        Asserts.isTrue(Objects.nonNull(id) && id != 0, SystemErrorCode.PARAM_REQUIRED, "id");
        Validations.validateSave(dto);

        McpToolProfilePo existing = mcpToolProfileDao.selectById(id);
        Asserts.isTrue(Objects.nonNull(existing), SystemErrorCode.DATA_NOT_EXISTS, id);
        Asserts.isTrue(Objects.equals(existing.getResourceType(), dto.getResourceType()),
            SystemErrorCode.DATA_NOT_EXISTS, id);

        // 只改展示与定义字段；名称、类型、服务、方法、路径由同步 / 导入决定，status 走 updateStatus
        McpToolProfilePo entity = new McpToolProfilePo();
        entity.setId(id);
        entity.setTitle(dto.getTitle());
        entity.setDescription(dto.getDescription());
        entity.setOpenapiDocument(dto.getOpenapiDocument());
        entity.setInputSchema(dto.getInputSchema());
        entity.setUpdateTime(Moments.now());
        int success = mcpToolProfileDao.update(entity);
        Asserts.greaterThan(success, 0, SystemErrorCode.UPDATE_DATA_ERROR, id);
        refreshShared(existing.getResourceType(), existing.getServiceId());
        return id;
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
        refreshShared(existing.getResourceType(), existing.getServiceId());
        return success;
    }

    @Override
    public int delete(Long id) {
        McpToolProfilePo existing = mcpToolProfileDao.selectById(id);
        Asserts.isTrue(Objects.nonNull(existing), SystemErrorCode.DATA_NOT_EXISTS, id);
        int deleted = mcpToolProfileDao.delete(id);
        refreshShared(existing.getResourceType(), existing.getServiceId());
        return deleted;
    }

    @Override
    public PageData<McpToolOptionVo> selectOptionPage(PageSelectListDto<McpToolOptionSelectDto> selectDto) {
        McpToolOptionSelectDto query = Objects.requireNonNullElseGet(selectDto.getQuery(), McpToolOptionSelectDto::new);
        Asserts.isTrue(isResourceType(query.getResourceType()), SystemErrorCode.PARAM_VAL_INVALID, "resourceType");
        Long organId = PrincipalContextHolder.getOrganId();

        Page<McpToolProfilePo> page = PageContext.of(selectDto.getPageNum(), selectDto.getPageSize(), () -> {
            mcpToolProfileDao.selectOptions(query, organId);
        });
        List<McpToolOptionVo> result = page.getResult()
            .stream()
            .map(McpToolProfileConverter.INSTANCE::po2option)
            .toList();
        fillOptionServiceNames(result);
        return PageData.of(page.getPageNum(), page.getPageSize(), page.getTotal(), result);
    }

    @Override
    public List<McpToolMetaVo> selectMetas(List<String> names) {
        if (Collections.isEmpty(names)) {
            return List.of();
        }

        Set<String> distinct = names.stream().filter(Strings::isNotBlank).collect(Collectors.toCollection(LinkedHashSet::new));
        Asserts.isTrue(distinct.size() <= MAX_NAMES, SystemErrorCode.PARAM_VAL_INVALID, "names");
        if (distinct.isEmpty()) {
            return List.of();
        }

        List<McpToolMetaVo> result = mcpToolProfileDao.selectMetas(distinct, PrincipalContextHolder.getOrganId())
            .stream()
            .map(McpToolProfileConverter.INSTANCE::po2meta)
            .toList();
        fillOptionServiceNames(result);
        return result;
    }

    private void refreshShared(String resourceType, Long serviceId) {
        if (ResourceType.NATIVE.name().equals(resourceType)) {
            nativeToolLoader.reload(serviceId);
        } else if (ResourceType.EXTENSION.name().equals(resourceType)) {
            extensionToolLoader.reload(serviceId);
        }
    }

    private static boolean isResourceType(String value) {
        for (ResourceType type : ResourceType.values()) {
            if (type.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private void fillProfileServiceNames(List<McpToolProfileVo> tools) {
        Map<String, Map<Long, String>> names = serviceNames(tools.stream()
            .map(tool -> new ServiceKey(tool.getResourceType(), tool.getServiceId())).toList());
        for (McpToolProfileVo tool : tools) {
            tool.setServiceName(names.getOrDefault(tool.getResourceType(), Map.of()).get(tool.getServiceId()));
        }
    }

    private void fillOptionServiceNames(List<? extends McpToolOptionVo> tools) {
        Map<String, Map<Long, String>> names = serviceNames(tools.stream()
            .map(tool -> new ServiceKey(tool.getResourceType(), tool.getServiceId())).toList());
        for (McpToolOptionVo tool : tools) {
            tool.setServiceName(names.getOrDefault(tool.getResourceType(), Map.of()).get(tool.getServiceId()));
        }
    }

    /**
     * 按资源类型分组取服务名称：资源类型 → (服务主键 → 名称)。
     */
    private Map<String, Map<Long, String>> serviceNames(List<ServiceKey> keys) {
        Map<String, Set<Long>> idsByType = new HashMap<>();
        for (ServiceKey key : keys) {
            if (Objects.nonNull(key.resourceType()) && Objects.nonNull(key.serviceId())) {
                idsByType.computeIfAbsent(key.resourceType(), type -> new HashSet<>()).add(key.serviceId());
            }
        }

        Map<String, Map<Long, String>> names = new HashMap<>();
        idsByType.forEach((type, ids) -> names.put(type, serviceNameResolver.names(type, ids)));
        return names;
    }

    private record ServiceKey(String resourceType, Long serviceId) {
    }
}
