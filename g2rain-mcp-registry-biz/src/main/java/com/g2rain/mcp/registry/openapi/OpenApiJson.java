package com.g2rain.mcp.registry.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.SpecVersion;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * 按 OpenAPI 版本选择 swagger-core 序列化器。
 * <p>
 * 3.1 模型把类型存在 {@code types}，必须用 {@link Json31#mapper()} 写出，否则 {@code type} 丢失；
 * 3.0（含由 Swagger 2.0 转出的 3.0）用 {@link Json#mapper()}。只在写入时使用，读取交给解析器按文档版本识别。
 * </p>
 *
 * @author alpha
 * @since 2026/10/27
 */
@NullMarked
public final class OpenApiJson {

    private OpenApiJson() {
    }

    /**
     * @param version 文档版本；为空按 3.0 处理
     * @return 对应版本的序列化器
     */
    public static ObjectMapper mapper(@Nullable SpecVersion version) {
        return version == SpecVersion.V31 ? Json31.mapper() : Json.mapper();
    }
}
