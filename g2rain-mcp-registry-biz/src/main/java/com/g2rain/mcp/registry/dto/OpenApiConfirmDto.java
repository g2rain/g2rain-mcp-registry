package com.g2rain.mcp.registry.dto;


import com.g2rain.mcp.registry.openapi.ConvertedOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 扩展系统 / 客户系统确认导入。只写入前端勾选的接口。
 *
 * @author alpha
 * @since 2026/10/16
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "OpenAPI 确认导入")
public class OpenApiConfirmDto {

    @Schema(description = "外部服务编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String serviceCode;

    @Schema(description = "EXTENSION 或 CUSTOMER", requiredMode = Schema.RequiredMode.REQUIRED)
    private String resourceType;

    @Schema(description = "勾选的接口")
    private List<ConvertedOperation> operations;
}
