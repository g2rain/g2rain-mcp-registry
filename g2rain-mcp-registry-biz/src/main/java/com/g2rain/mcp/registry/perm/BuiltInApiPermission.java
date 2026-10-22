package com.g2rain.mcp.registry.perm;



import lombok.extern.slf4j.Slf4j;
import com.g2rain.basis.enums.AuthorizationStatus;
import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.PrincipalContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 内置工具调用前的接口权限校验。
 * <p>
 * 分支与网关 {@code ApiPermissionFilter} 一致：账号看共用权限，会员和匿名访客看机构权限，用户看应用下的授权状态。
 * 扩展工具和客户工具不走这里。
 * </p>
 *
 * @author alpha
 * @since 2026/10/16
 */
@Component
@Slf4j
public class BuiltInApiPermission {

    private final PassportApiPerm passportApiPerm;

    private final MemberApiPerm memberApiPerm;

    private final AnonymousApiPerm anonymousApiPerm;

    private final UserApiPerm userApiPerm;

    /**
     * @param passportApiPerm 账号权限
     * @param memberApiPerm   会员权限
     * @param anonymousApiPerm 匿名访客权限
     * @param userApiPerm     用户权限
     */
    public BuiltInApiPermission(PassportApiPerm passportApiPerm, MemberApiPerm memberApiPerm,
                                AnonymousApiPerm anonymousApiPerm, UserApiPerm userApiPerm) {
        this.passportApiPerm = passportApiPerm;
        this.memberApiPerm = memberApiPerm;
        this.anonymousApiPerm = anonymousApiPerm;
        this.userApiPerm = userApiPerm;
    }

    /**
     * 校验当前登录身份能否调用这个内置接口。
     *
     * @param apiId {@code resource_api.id}，也就是内置工具的 {@code sourceId}
     * @throws BusinessException 未登录、身份不完整或没有权限
     */
    public void check(Long apiId) {
        if (Objects.isNull(apiId)) {
            throw new BusinessException(SystemErrorCode.UNAUTHORIZED, PrincipalContextHolder.getApplicationId());
        }
        SessionType sessionType = PrincipalContextHolder.getSessionType();
        if (Objects.isNull(sessionType)) {
            throw new BusinessException(SystemErrorCode.UNAUTHENTICATED, "session");
        }
        if (SessionType.isPassport(sessionType)) {
            if (passportApiPerm.hasApiPermission(apiId)) {
                return;
            }
            throw new BusinessException(SystemErrorCode.UNAUTHORIZED, PrincipalContextHolder.getApplicationId());
        }
        if (SessionType.isMember(sessionType)) {
            if (!validMember()) {
                throw new BusinessException(SystemErrorCode.UNAUTHENTICATED, "MEMBER");
            }
            try {
                if (memberApiPerm.hasApiPermission(PrincipalContextHolder.getOrganId(), apiId)) {
                    return;
                }
            } catch (BusinessException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                log.error("failed to check", ex);
                throw new BusinessException(SystemErrorCode.SYSTEM_INTERNAL_ERROR, "member permission");
            }
            throw new BusinessException(SystemErrorCode.UNAUTHORIZED, PrincipalContextHolder.getApplicationId());
        }
        if (SessionType.isAnonymous(sessionType)) {
            if (!validAnonymous()) {
                throw new BusinessException(SystemErrorCode.UNAUTHENTICATED, "ANONYMOUS");
            }
            try {
                if (anonymousApiPerm.hasApiPermission(PrincipalContextHolder.getOrganId(), apiId)) {
                    return;
                }
            } catch (BusinessException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                log.error("failed to check", ex);
                throw new BusinessException(SystemErrorCode.SYSTEM_INTERNAL_ERROR, "anonymous permission");
            }
            throw new BusinessException(SystemErrorCode.UNAUTHORIZED, PrincipalContextHolder.getApplicationId());
        }
        String status = userApiPerm.status(
            PrincipalContextHolder.getOrganId(),
            PrincipalContextHolder.getUserId(),
            PrincipalContextHolder.getApplicationId(),
            apiId
        );
        if (Strings.equals(AuthorizationStatus.ACTIVATED.name(), status)) {
            return;
        }
        throw new BusinessException(SystemErrorCode.UNAUTHORIZED, PrincipalContextHolder.getApplicationId());
    }

    /**
     * 访客：机构与访客 ID（userId）为正，无账号与会员主体。
     */
    private static boolean validAnonymous() {
        Long organId = PrincipalContextHolder.getOrganId();
        Long userId = PrincipalContextHolder.getUserId();
        return Objects.nonNull(organId) && organId > 0
            && Objects.nonNull(userId) && userId > 0
            && Objects.isNull(PrincipalContextHolder.getPassportId())
            && Objects.isNull(PrincipalContextHolder.getMemberId());
    }

    private static boolean validMember() {
        Long organId = PrincipalContextHolder.getOrganId();
        Long memberId = PrincipalContextHolder.getMemberId();
        return Objects.nonNull(organId) && organId > 0
            && Objects.nonNull(memberId) && memberId > 0
            && Objects.isNull(PrincipalContextHolder.getUserId())
            && Objects.isNull(PrincipalContextHolder.getPassportId());
    }
}
