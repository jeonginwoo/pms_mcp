package kr.proten.pms.person.service.impl;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.lenient;

import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotImplementedException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.service.dto.GradeCommand;
import kr.proten.pms.person.service.dto.PermissionGroupCommand;
import kr.proten.pms.person.service.dto.UpdatePersonCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 골격 유스케이스의 권한 판정 — EPIC E 쓰기 경로 (E2-2·E1-1·E4·E5).
 *
 * 로직은 아직 없지만 **403은 지금 성립해야 한다**: 관리 플래그 없는 호출자가 501을
 * 받으면 그 경로가 존재한다는 사실과 곧 열린다는 사실을 함께 알게 되고, 구현이
 * 들어오는 날에야 403이 뒤늦게 생긴다. 없는 것은 로직이지 권한이 아니다.
 *
 * 그래서 이 테스트가 잠그는 것은 두 가지다 — 플래그가 없으면 403이고, 있으면
 * (아직) 501이다. 두 번째가 없으면 판정이 통과했는지 알 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class ScaffoldAuthorizationTest {
    private static final long ADMIN_ID = 1L;
    private static final long MEMBER_ID = 28L;

    @Mock
    private OrgPermissionService orgPermissionService;

    private PersonServiceImpl personService;
    private GradeServiceImpl gradeService;
    private PermissionGroupServiceImpl permissionGroupService;

    @BeforeEach
    void setUp() {
        personService = new PersonServiceImpl(null, null, null, null, null, null,
                orgPermissionService, null, null, null);
        gradeService = new GradeServiceImpl(null, orgPermissionService);
        permissionGroupService = new PermissionGroupServiceImpl(null, orgPermissionService);
    }

    @Test
    @DisplayName("관리 플래그가 없으면 골격 경로도 403이다 — 501로 새지 않는다")
    void scaffoldWrites_withoutManageOrg_areForbidden() {
        // Given
        givenManageOrg(MEMBER_ID, false);

        // When · Then
        assertForbidden(() -> personService.update(MEMBER_ID, updatePersonCommand()));
        assertForbidden(() -> personService.moveOrgUnit(MEMBER_ID, 103L, 5L));
        assertForbidden(() -> gradeService.create(MEMBER_ID, gradeCommand()));
        assertForbidden(() -> gradeService.update(MEMBER_ID, gradeCommand()));
        assertForbidden(() -> gradeService.delete(MEMBER_ID, 1L));
        assertForbidden(() -> permissionGroupService.create(MEMBER_ID, groupCommand()));
        assertForbidden(() -> permissionGroupService.update(MEMBER_ID, groupCommand()));
        assertForbidden(() -> permissionGroupService.delete(MEMBER_ID, 4L));
    }

    @Test
    @DisplayName("플래그가 있으면 판정을 통과해 501에 닿는다 — 구현이 들어올 자리다")
    void scaffoldWrites_withManageOrg_reachNotImplemented() {
        // Given
        givenManageOrg(ADMIN_ID, true);

        // When · Then
        assertNotImplemented(() -> personService.update(ADMIN_ID, updatePersonCommand()));
        assertNotImplemented(() -> personService.moveOrgUnit(ADMIN_ID, 103L, 5L));
        assertNotImplemented(() -> gradeService.create(ADMIN_ID, gradeCommand()));
        assertNotImplemented(() -> gradeService.update(ADMIN_ID, gradeCommand()));
        assertNotImplemented(() -> gradeService.delete(ADMIN_ID, 1L));
        assertNotImplemented(() -> permissionGroupService.create(ADMIN_ID, groupCommand()));
        assertNotImplemented(() -> permissionGroupService.update(ADMIN_ID, groupCommand()));
        assertNotImplemented(() -> permissionGroupService.delete(ADMIN_ID, 4L));
    }

    private void givenManageOrg(long callerPersonId, boolean allowed) {
        // 여러 호출을 한 테스트에서 돌리므로 stubbing 사용 횟수는 검증 대상이 아니다
        lenient().when(orgPermissionService.has(callerPersonId, OrgPermission.MANAGE_ORG))
                .thenReturn(allowed);
    }

    private void assertForbidden(Runnable call) {
        assertThatExceptionOfType(ForbiddenException.class).isThrownBy(call::run);
    }

    private void assertNotImplemented(Runnable call) {
        assertThatExceptionOfType(NotImplementedException.class).isThrownBy(call::run);
    }

    private UpdatePersonCommand updatePersonCommand() {
        return new UpdatePersonCommand(103L, "홍길동", 5L, 1L, 4L, 0L);
    }

    private GradeCommand gradeCommand() {
        return new GradeCommand(1L, "선임", 1.0, 0L);
    }

    private PermissionGroupCommand groupCommand() {
        return new PermissionGroupCommand(4L, "팀원", "TEAM", false, false, false, false, 0L);
    }
}
