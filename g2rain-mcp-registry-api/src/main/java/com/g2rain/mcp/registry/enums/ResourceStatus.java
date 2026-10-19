package com.g2rain.mcp.registry.enums;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 外部服务启用状态。
 */
@Schema(description = "外部服务启用状态")
public enum ResourceStatus {

    @Schema(description = "已启用")
    ACTIVE,

    @Schema(description = "已停用")
    INACTIVE;

    /**
     * 校验状态名是否为合法枚举值。
     *
     * @param name 状态字符串
     * @throws BusinessException 名称无效时抛出
     */
    public static void validate(String name) {
        if (Strings.isNotBlank(name)) {
            for (ResourceStatus status : values()) {
                if (status.name().equals(name)) {
                    return;
                }
            }
        }
        throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, name);
    }
}
