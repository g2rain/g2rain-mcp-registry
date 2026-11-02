package com.g2rain.mcp.registry.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Asserts;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.dao.McpToolProfileDao;
import com.g2rain.mcp.registry.dao.po.McpToolProfilePo;
import com.g2rain.mcp.registry.enums.McpErrorCode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工具名冲突校验：同一机构 + 同一资源类型内工具名唯一（三类统一，与唯一索引 {@code uk_name} 口径一致）。
 * <p>
 * 跨资源类型不比较：三类前缀不同，不会重名。同一工具重复导入（同一 id）不算冲突；
 * 名称被其他服务的工具占用时报 {@link McpErrorCode#TOOL_NAME_CONFLICT}，提示占用的服务。
 * </p>
 */
@Component
public class McpToolNameGuard {

    @Resource(name = "mcpToolProfileDao")
    private McpToolProfileDao mcpToolProfileDao;

    @Resource
    private ServiceNameResolver serviceNameResolver;

    /**
     * 导入前一次取齐范围内已占用的工具名，供内存判重。
     *
     * @param resourceType 资源类型名
     * @param organId      机构
     * @return 工具名 → 占用的工具（id、service_id）
     */
    public Map<String, McpToolProfilePo> loadOwners(String resourceType, Long organId) {
        List<McpToolProfilePo> rows = mcpToolProfileDao.selectNameOwners(resourceType, organId);
        Map<String, McpToolProfilePo> owners = new HashMap<>();
        if (Collections.isEmpty(rows)) {
            return owners;
        }

        for (McpToolProfilePo row : rows) {
            owners.put(row.getName(), row);
        }
        return owners;
    }

    /**
     * 登记一个工具名；已被其他工具占用时抛出冲突。调用前 {@code tool} 须已有 id。
     *
     * @param owners 范围内已占用的工具名
     * @param tool   待写入的工具
     * @throws BusinessException 名称被其他服务的工具占用
     */
    public void claim(Map<String, McpToolProfilePo> owners, McpToolProfilePo tool) {
        Asserts.isTrue(Strings.isNotBlank(tool.getName()), SystemErrorCode.PARAM_REQUIRED, "name");
        McpToolProfilePo owner = owners.get(tool.getName());
        if (Objects.nonNull(owner) && !Objects.equals(owner.getId(), tool.getId())) {
            String service = serviceNameResolver.name(tool.getResourceType(), owner.getServiceId());
            throw new BusinessException(McpErrorCode.TOOL_NAME_CONFLICT, tool.getName(), service);
        }

        owners.put(tool.getName(), tool);
    }
}
