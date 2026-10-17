package com.g2rain.mcp.registry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 状态修改 DTO
 */
@Setter
@Getter
@NoArgsConstructor
@Schema(description = "状态修改 DTO")
public class UpdateStatusDto {

    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "业务状态")
    private String status;
}
