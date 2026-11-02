package com.g2rain.mcp.registry.runtime;

import com.g2rain.common.utils.Strings;
import com.g2rain.mcp.registry.catalog.OpenApiOperation;
import com.g2rain.mcp.registry.dao.po.McpToolProfilePo;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * 把工具档案和调用基址收成一条 {@link OpenApiOperation}。
 * <p>
 * 档案的 {@code openapi_document} 是单接口文档，交给 {@link OpenAPIParser} 解析，解析器按文档的 {@code openapi}
 * 字段自动以 3.0 或 3.1 读取；文档中 {@code $ref} 已展开，这里不再展开。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Slf4j
@Component
public class ToolOperationAssembler {

    private final OpenAPIParser parser = new OpenAPIParser();

    /**
     * 组装运行时操作。
     *
     * @param profile  工具档案
     * @param endpoint 调用基址
     * @return 可注册的操作；方法或文档无法解析时返回 {@code null}
     */
    public @Nullable OpenApiOperation assemble(McpToolProfilePo profile, @Nullable String endpoint) {
        if (Objects.isNull(profile) || Strings.isBlank(endpoint) || Strings.isBlank(profile.getPath())
            || Strings.isBlank(profile.getMethod())) {
            return null;
        }

        PathItem.HttpMethod method;
        try {
            method = PathItem.HttpMethod.valueOf(profile.getMethod().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("工具 HTTP 方法无法解析 name={} method={}", profile.getName(), profile.getMethod());
            return null;
        }

        Operation operation = readOperation(profile, method);
        if (Objects.isNull(operation)) {
            return null;
        }

        return new OpenApiOperation(endpoint, profile.getPath(), method, operation);
    }

    /**
     * 解析单接口文档，取出档案 path + method 对应的 Operation。
     */
    private @Nullable Operation readOperation(McpToolProfilePo profile, PathItem.HttpMethod method) {
        if (Strings.isBlank(profile.getOpenapiDocument())) {
            log.warn("工具缺少 OpenAPI 文档 name={}", profile.getName());
            return null;
        }

        try {
            SwaggerParseResult result = parser.readContents(profile.getOpenapiDocument(), null, new ParseOptions());
            OpenAPI openAPI = result.getOpenAPI();
            PathItem pathItem = Objects.isNull(openAPI) || Objects.isNull(openAPI.getPaths())
                ? null : openAPI.getPaths().get(profile.getPath());
            Operation operation = Objects.isNull(pathItem) ? null : pathItem.readOperationsMap().get(method);
            if (Objects.isNull(operation)) {
                log.warn("工具 OpenAPI 文档中找不到接口 name={} method={} path={}",
                    profile.getName(), method, profile.getPath());
            }
            return operation;
        } catch (RuntimeException ex) {
            log.warn("工具 OpenAPI 无法解析 name={}", profile.getName(), ex);
            return null;
        }
    }
}
