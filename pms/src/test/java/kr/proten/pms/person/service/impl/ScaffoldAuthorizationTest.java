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
 * 골격 유스케이스의 권한 판정 — EPIC E 쓰기 경로 전부 (E1-1·E2-2·E3-2·E4·E5).
 *
 * **2026-08-24 — 501 절반이 사라졌다**: EPIC E 쓰기가 전부 구현돼 "플래그가 있으면 501에
 * 닿는다"는 케이스는 더 이상 참이 아니다(골격 주석이 "구현이 들어오면 던지는 자리가
 * 사라진다"고 예고한 그것이다). 남은 절반이 이 클래스의 본체다 — **권한 판정이 다른
 * 무엇보다 먼저 온다**는 것은 로직이 들어와도 계속 참이어야 하고, 오히려 지금부터
 * 깨지기 쉽다(저장소 조회가 판정보다 앞서면 404·422가 403을 가린다).
 *
 * 구 주석: 로직은 아직 없지만 **403은 지금 성립해야 한다** — 관리 플래그 없는 호출자가 501을
 * 받으면 그 경로가 존재한다는 사실과 곧 열린다는 사실을 함께 알게 되고, 구현이
 * 들어오는 날에야 403이 뒤늦게 생긴다. 없는 것은 로직이지 권한이 아니다.
 *
 * 잠그는 것은 두 가지다 — 플래그가 없으면 403이고, 있으면 (아직) 501이다.
 * 두 번째가 없으면 판정이 통과했는지 알 수 없다.
 *
 * **모든 EPIC E 쓰기 경로를 여기 한 목록에 모아 둔다**: 판정을 유스케이스마다 복사하던
 * 동안 조직 개명(E3-2)이 빠져 있었고 어떤 테스트도 깨지지 않았다(2026-08-22 리뷰 발견).
 * 새 쓰기 경로를 만들면 이 목록에도 한 줄 추가한다.
 *
 * 협력자는 실물 `OrgManagePermission`을 쓴다 — 판정 자체가 검증 대상이라 그것까지
 * 목으로 바꾸면 "플래그를 실제로 물어본다"는 사실이 빠진다. 유스케이스가 판정 뒤에
 * 쓰는 저장소들은 이 경로에 도달하지 않으므로 주입하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class ScaffoldAuthorizationTest {
    private static final long ADMIN_ID = 1L;
    private static final long MEMBER_ID = 28L;

    @Mock
    private OrgPermissionService orgPermissionService;

    private PersonServiceImpl personService;
    private OrgUnitServiceImpl orgUnitService;
    private GradeServiceImpl gradeService;
    private PermissionGroupServiceImpl permissionGroupService;

    @BeforeEach
    void setUp() {
        OrgManagePermission orgManagePermission = new OrgManagePermission(orgPermissionService);
        // 협력자는 전부 null이다 — 판정에서 막히면 그 뒤로 한 줄도 가지 않는다는 것이
        // 이 테스트가 증명하는 것이고, null이 그 증명을 대신한다(가면 NPE로 실패한다)
        personService = new PersonServiceImpl(null, null, null, null, null, null,
                orgManagePermission, null, null, null, null);
        orgUnitService = new OrgUnitServiceImpl(null, null, orgManagePermission, null);
        gradeService = new GradeServiceImpl(null, null, orgManagePermission, null);
        permissionGroupService =
                new PermissionGroupServiceImpl(null, null, orgManagePermission, null);
    }

    @Test
    @DisplayName("관리 플래그가 없으면 EPIC E 쓰기는 전부 403이다 — 판정이 가장 먼저다")
    void orgWrites_withoutManageOrg_areForbidden() {
        // Given
        givenManageOrg(MEMBER_ID, false);

        // When · Then
        assertForbidden(() -> personService.update(MEMBER_ID, updatePersonCommand()));
        assertForbidden(() -> personService.moveOrgUnit(MEMBER_ID, 103L, 5L));
        assertForbidden(() -> orgUnitService.rename(MEMBER_ID, 5L, "새이름"));
        assertForbidden(() -> gradeService.create(MEMBER_ID, gradeCommand()));
        assertForbidden(() -> gradeService.update(MEMBER_ID, gradeCommand()));
        assertForbidden(() -> gradeService.delete(MEMBER_ID, 1L));
        assertForbidden(() -> permissionGroupService.create(MEMBER_ID, groupCommand()));
        assertForbidden(() -> permissionGroupService.update(MEMBER_ID, groupCommand()));
        assertForbidden(() -> permissionGroupService.delete(MEMBER_ID, 4L));
    }

    @Test
    @DisplayName("권한 그룹 **조회**도 관리 플래그가 없으면 403이다 — 인력 목록 열의 실제 방어선")
    void permissionGroupList_withoutManageOrg_isForbidden() {
        givenManageOrg(MEMBER_ID, false);

        /*
         * 2026-08-26 인력 목록에 권한 그룹 열을 붙이며 이 403이 판단의 근거가 됐다:
         * 서버 DTO에 그룹 **이름**을 싣지 않고 화면이 이 목록으로 id → 이름을 해석하므로,
         * 비관리자에게 이름이 가지 않는 이유는 화면 조건이 아니라 **이 거절**이다.
         * 쓰기 셋만 잠겨 있고 읽기는 안 잠겨 있었다.
         */
        assertForbidden(() -> permissionGroupService.list(MEMBER_ID));
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
        return new UpdatePersonCommand(103L, "홍길동", 5L, 1L, 4L, true, 0L);
    }

    private GradeCommand gradeCommand() {
        return new GradeCommand(1L, "선임", 1.0, 0L);
    }

    private PermissionGroupCommand groupCommand() {
        return new PermissionGroupCommand(4L, "팀원", "TEAM", false, false, false, false, 0L);
    }
}
