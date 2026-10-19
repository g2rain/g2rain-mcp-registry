package com.g2rain.mcp.registry.runtime;


import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.dao.po.McpToolProfilePo;
import com.g2rain.mcp.registry.openapi.naming.EndpointStrategy;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 把工具档案和调用地址收成一条 {@link OpenApiOperation}。
 *
 * @author alpha
 * @since 2026/10/16
 */
@Slf4j
@Component
public class ToolOperationAssembler {

    /**
     * 组装运行时操作。
     *
     * @param profile     工具档案
     * @param endpoint    服务地址
     * @param contextPath 上下文路径
     * @return 可注册的操作；方法或文档无法解析时返回 {@code null}
     */
    public OpenApiOperation assemble(McpToolProfilePo profile, String endpoint, String contextPath) {
        if (Objects.isNull(profile) || Strings.isBlank(endpoint) || Strings.isBlank(profile.getPath()) || Strings.isBlank(profile.getMethod())) {
            return null;
        }
        PathItem.HttpMethod method;
        try {
            method = PathItem.HttpMethod.valueOf(profile.getMethod());
        } catch (IllegalArgumentException ex) {
            log.warn("工具 HTTP 方法无法解析 name={} method={}", profile.getName(), profile.getMethod());
            return null;
        }
        Operation operation = readOperation(profile);
        if (Objects.isNull(operation)) {
            return null;
        }
        String path = profile.getPath().startsWith("/") ? profile.getPath() : "/" + profile.getPath();
        String callBase = new ServiceEndpointCache.Endpoint(endpoint, Strings.isBlank(contextPath) ? "" : contextPath).callBase();
        String serviceSegment = EndpointStrategy.resolveServiceSegment(profile.getResourceType(), profile.getServiceCode());
        return new OpenApiOperation(callBase, contextPath, path, method, operation, serviceSegment);
    }

    private Operation readOperation(McpToolProfilePo profile) {
        if (Strings.isBlank(profile.getOpenapiOperation())) {
            return new Operation();
        }
        try {
            return Json.mapper().readValue(profile.getOpenapiOperation(), Operation.class);
        } catch (Exception ex) {
            log.warn("工具 OpenAPI 无法解析 name={}", profile.getName(), ex);
            return null;
        }
    }
}
