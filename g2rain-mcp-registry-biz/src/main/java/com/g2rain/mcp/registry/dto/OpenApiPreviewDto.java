package com.g2rain.mcp.registry.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 扩展系统 / 客户系统的 OpenAPI 预览入参。不写库。
 *
 * @author alpha
 * @since 2026/10/16
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "OpenAPI 预览")
public class OpenApiPreviewDto {

    @Schema(description = "整份 OpenAPI / Swagger 的 JSON 或 YAML")
    private String document;

    @Schema(description = "资源类型：EXTENSION / CUSTOMER；用于建议工具名")
    private String resourceType;

    @Schema(description = "服务编码；用于建议工具名中间段")
    private String serviceCode;

    @Schema(description = "上下文路径；可空，空则服务端按服务档案补全")
    private String contextPath;
}
