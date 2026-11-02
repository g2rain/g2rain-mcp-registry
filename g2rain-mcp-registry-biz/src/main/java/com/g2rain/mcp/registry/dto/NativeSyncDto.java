package com.g2rain.mcp.registry.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 原生工具同步入参。
 * <p>
 * {@code importAll} 为 true 时导入该服务下全部资源接口。否则只导入 {@code resourceApiIds}。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "原生工具同步")
public class NativeSyncDto {

    @Schema(description = "服务注册主键，对应 service_registry.id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long serviceId;

    @Schema(description = "选中的 resource_api.id；importAll 为 true 时可空")
    private List<Long> resourceApiIds;

    @Schema(description = "是否导入该服务下的全部接口")
    private boolean importAll;
}
