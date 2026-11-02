package com.g2rain.mcp.registry.service;

import com.g2rain.common.utils.Collections;
import com.g2rain.mcp.registry.dao.McpExternalServiceDao;
import com.g2rain.mcp.registry.dao.po.McpExternalServicePo;
import com.g2rain.mcp.registry.dto.McpExternalServiceSelectDto;
import com.g2rain.mcp.registry.enums.ResourceType;
import com.g2rain.mcp.registry.runtime.ServiceEndpointCache;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工具所属服务的名称：NATIVE 取服务注册名称，EXTENSION / CUSTOMER 取外部服务名称。
 *
 * @author alpha
 * @since 2026/10/27
 */
@Component
public class ServiceNameResolver {

    @Resource
    private ServiceEndpointCache serviceEndpointCache;

    @Resource(name = "mcpExternalServiceDao")
    private McpExternalServiceDao mcpExternalServiceDao;

    /**
     * 单个服务名称。
     *
     * @param resourceType 资源类型名
     * @param serviceId    服务主键
     * @return 服务名称；找不到时为服务主键文本
     */
    public String name(String resourceType, Long serviceId) {
        String name = names(resourceType, List.of(serviceId)).get(serviceId);
        return Objects.nonNull(name) ? name : String.valueOf(serviceId);
    }

    /**
     * 批量服务名称。
     *
     * @param resourceType 资源类型名
     * @param serviceIds   服务主键
     * @return 服务主键 → 名称；找不到的不在结果中
     */
    public Map<Long, String> names(String resourceType, Collection<Long> serviceIds) {
        Map<Long, String> names = new HashMap<>();
        if (Collections.isEmpty(serviceIds)) {
            return names;
        }

        if (ResourceType.NATIVE.name().equals(resourceType)) {
            for (Long serviceId : serviceIds) {
                String name = serviceEndpointCache.name(serviceId);
                if (Objects.nonNull(name)) {
                    names.put(serviceId, name);
                }
            }
            return names;
        }

        McpExternalServiceSelectDto select = new McpExternalServiceSelectDto();
        select.setIds(new HashSet<>(serviceIds));
        for (McpExternalServicePo service : mcpExternalServiceDao.selectListWithoutIsolation(select)) {
            names.put(service.getId(), service.getName());
        }
        return names;
    }
}
