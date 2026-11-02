package com.g2rain.mcp.registry.dto;


import com.g2rain.mcp.registry.openapi.ConvertedOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 扩展系统 / 租户系统确认导入。只写入前端勾选的接口；工具名由服务端按单接口文档重新生成。
 *
 * @author alpha
 * @since 2026/10/16
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "OpenAPI 确认导入")
public class OpenApiConfirmDto {

    @Schema(description = "外部服务主键；资源类型取自外部服务记录", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long serviceId;

    @Schema(description = "勾选的接口")
    private List<ConvertedOperation> operations;
}
