package com.g2rain.mcp.registry.perm;

import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.web.PrincipalContext;
import com.g2rain.common.web.PrincipalContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NativeApiPermissionTest {

    private AnonymousApiPerm anonymousApiPerm;
    private UserApiPerm userApiPerm;
    private NativeApiPermission permission;

    @BeforeEach
    void setUp() {
        anonymousApiPerm = mock(AnonymousApiPerm.class);
        userApiPerm = mock(UserApiPerm.class);
        permission = new NativeApiPermission(mock(PassportApiPerm.class), mock(MemberApiPerm.class),
            anonymousApiPerm, userApiPerm);
    }

    @Test
    void anonymous_allowedWhenOrganOpenedApi() {
        when(anonymousApiPerm.hasApiPermission(10001L, 42L)).thenReturn(true);

        runAsVisitor(ctx -> { }, () -> assertDoesNotThrow(() -> permission.check(42L)));
        verify(userApiPerm, never()).status(any(), any(), any(), anyLong());
    }

    @Test
    void anonymous_rejectedWhenOrganNotOpened() {
        when(anonymousApiPerm.hasApiPermission(10001L, 42L)).thenReturn(false);

        runAsVisitor(ctx -> { }, () -> {
            BusinessException ex = assertThrows(BusinessException.class, () -> permission.check(42L));
            assertEquals(SystemErrorCode.UNAUTHORIZED.code(), ex.getErrorCode());
        });
    }

    @Test
    void anonymous_withoutVisitorId_isUnauthenticated() {
        runAsVisitor(ctx -> ctx.setUserId(null), () -> {
            BusinessException ex = assertThrows(BusinessException.class, () -> permission.check(42L));
            assertEquals(SystemErrorCode.UNAUTHENTICATED.code(), ex.getErrorCode());
        });
        verify(anonymousApiPerm, never()).hasApiPermission(anyLong(), anyLong());
    }

    @Test
    void anonymous_withPassport_isUnauthenticated() {
        runAsVisitor(ctx -> ctx.setPassportId(1L), () ->
            assertThrows(BusinessException.class, () -> permission.check(42L)));
        verify(anonymousApiPerm, never()).hasApiPermission(anyLong(), anyLong());
    }

    @Test
    void anonymous_snapshotFailure_isInternalError() {
        when(anonymousApiPerm.hasApiPermission(10001L, 42L)).thenThrow(new IllegalStateException("basis down"));

        runAsVisitor(ctx -> { }, () -> {
            BusinessException ex = assertThrows(BusinessException.class, () -> permission.check(42L));
            assertEquals(SystemErrorCode.SYSTEM_INTERNAL_ERROR.code(), ex.getErrorCode());
        });
    }

    private static void runAsVisitor(Consumer<PrincipalContext> customizer, Runnable action) {
        PrincipalContext context = PrincipalContext.of();
        context.setApplicationId(1001L);
        context.setSessionType(SessionType.ANONYMOUS);
        context.setOrganId(10001L);
        context.setUserId(77L);
        customizer.accept(context);
        PrincipalContextHolder.runWith(context, action);
    }
}
