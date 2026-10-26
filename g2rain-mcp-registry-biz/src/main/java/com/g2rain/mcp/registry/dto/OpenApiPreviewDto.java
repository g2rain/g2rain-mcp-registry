package com.g2rain.mcp.registry.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 扩展系统 / 租户系统的 OpenAPI 预览入参。不写库。
 * <p>
 * 资源类型取自外部服务记录，不由前端传入。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "OpenAPI 预览")
public class OpenApiPreviewDto {

    @Schema(description = "整份 OpenAPI / Swagger 的 JSON 或 YAML", requiredMode = Schema.RequiredMode.REQUIRED)
    private String document;

    @Schema(description = "外部服务主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long serviceId;
}
