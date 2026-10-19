package com.g2rain.mcp.registry.service;


import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.dao.McpToolProfileDao;
import com.g2rain.mcp.registry.enums.ResourceType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 工具名冲突校验（共享工具 vs 全量 CUSTOMER；CUSTOMER vs 共享+本机构）。
 */
@Component
public class McpToolNameGuard {

    @Resource(name = "mcpToolProfileDao")
    private McpToolProfileDao mcpToolProfileDao;

    /**
     * 单条保存时的唯一性断言。
     */
    public void assertUnique(String name, String resourceType, Long organId, Long excludeId) {
        Asserts.isTrue(Strings.isNotBlank(name), SystemErrorCode.PARAM_REQUIRED, "name");
        Asserts.isTrue(Strings.isNotBlank(resourceType), SystemErrorCode.PARAM_REQUIRED, "resourceType");
        if (ResourceType.CUSTOMER.name().equals(resourceType)) {
            Asserts.isTrue(Objects.nonNull(organId), SystemErrorCode.PARAM_REQUIRED, "organId");
        }
        Asserts.isTrue(mcpToolProfileDao.countNameConflict(name, resourceType, organId, excludeId) == 0,
            SystemErrorCode.DATA_EXISTS, name);
    }

    /**
     * 导入前一次拉齐冲突范围内已占用名称，供内存判重。
     */
    public Set<String> loadOccupiedNames(String resourceType, Long organId) {
        List<String> names = mcpToolProfileDao.selectConflictNames(resourceType, organId);
        return Collections.isEmpty(names) ? new HashSet<>() : new HashSet<>(names);
    }

    /**
     * 在占用集合上登记一个新名；若已存在则抛 {@link SystemErrorCode#DATA_EXISTS}。
     * 更新场景先 {@link Set#remove} 旧名再调用本方法。
     */
    public void claim(Set<String> occupied, String name) {
        Asserts.isTrue(Strings.isNotBlank(name), SystemErrorCode.PARAM_REQUIRED, "name");
        Asserts.isTrue(!occupied.contains(name), SystemErrorCode.DATA_EXISTS, name);
        occupied.add(name);
    }
}
