package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 기능별 권한 기본 매트릭스 단위 테스트 (상위 PRD §4-2).
 *
 * 표가 실제로 그 표인지 확인하는 자리다: 정보 수정은 PM·PL(A5-3) · 배정은 PM(B1-4) ·
 * 진척률과 완료·재개는 배정 전원(A2-1·A7-1). 프로젝트별 커스텀(US-A8)이 들어오면
 * 여기 기대값 위에 override 케이스가 붙는다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectActionPermissionTest {
    private static final long PROJECT_ID = 7L;
    private static final long CALLER_ID = 103L;

    @Mock
    private ProjectRoleResolver projectRoleResolver;
    @Mock
    private OrgPermissionService orgPermissionService;

    private ProjectActionPermission permission;

    @BeforeEach
    void setUp() {
        permission = new ProjectActionPermission(projectRoleResolver, orgPermissionService);
    }

    @Test
    @DisplayName("A5-3 — 정보 수정은 PM·PL만, 참여자는 403")
    void require_editInfo_allowsManagerAndLeadOnly() {
        givenRole(ProjectRole.PM);
        assertThatNoException().isThrownBy(() -> require(ProjectAction.EDIT_INFO));

        givenRole(ProjectRole.PL);
        assertThatNoException().isThrownBy(() -> require(ProjectAction.EDIT_INFO));

        givenRole(ProjectRole.PARTICIPANT);
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> require(ProjectAction.EDIT_INFO));
    }

    @Test
    @DisplayName("B1-4 — 배정은 PM만, PL·참여자는 403 (M/M 입력은 PM의 일)")
    void require_assign_allowsManagerOnly() {
        givenRole(ProjectRole.PM);
        assertThatNoException().isThrownBy(() -> require(ProjectAction.ASSIGN));

        givenRole(ProjectRole.PL);
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> require(ProjectAction.ASSIGN));

        givenRole(ProjectRole.PARTICIPANT);
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> require(ProjectAction.ASSIGN));
    }

    @Test
    @DisplayName("A2-1·A7-1 — 진척률과 완료·재개는 배정 전원(역할 무관)")
    void require_progressAndCompleteReopen_allowEveryAssignedRole() {
        for (ProjectRole role : ProjectRole.values()) {
            givenRole(role);
            assertThatNoException().isThrownBy(() -> require(ProjectAction.PROGRESS));
            assertThatNoException().isThrownBy(() -> require(ProjectAction.COMPLETE_REOPEN));
        }
    }

    @Test
    @DisplayName("A2-4·A7-5 — 미배정은 403 (가시성 밖이면 이 판정에 닿기 전에 404다)")
    void require_unassigned_isForbidden() {
        when(projectRoleResolver.roleOf(CALLER_ID, PROJECT_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> require(ProjectAction.PROGRESS));
    }

    @Test
    @DisplayName("A4-1 — 삭제는 PM 또는 '프로젝트 생성' 플래그 보유자 (2026-08-22 결정)")
    void requireDelete_allowsManagerOrProjectCreator() {
        // PM은 통과
        givenCreateProjectFlag(false);
        givenRole(ProjectRole.PM);
        assertThatNoException().isThrownBy(this::requireDelete);

        // 생성 권한자는 배정되지 않았어도 통과 — 플래그가 있으면 역할을 보지도 않는다
        givenCreateProjectFlag(true);
        assertThatNoException().isThrownBy(this::requireDelete);
    }

    @Test
    @DisplayName("A4-2 — PL·참여자는 삭제 403 (생성 플래그도 없을 때)")
    void requireDelete_rejectsLeadAndParticipant() {
        givenCreateProjectFlag(false);

        givenRole(ProjectRole.PL);
        assertThatExceptionOfType(ForbiddenException.class).isThrownBy(this::requireDelete);

        givenRole(ProjectRole.PARTICIPANT);
        assertThatExceptionOfType(ForbiddenException.class).isThrownBy(this::requireDelete);
    }

    private void require(ProjectAction action) {
        permission.require(CALLER_ID, PROJECT_ID, action);
    }

    private void requireDelete() {
        permission.requireDelete(CALLER_ID, PROJECT_ID);
    }

    private void givenCreateProjectFlag(boolean granted) {
        when(orgPermissionService.has(CALLER_ID, OrgPermission.CREATE_PROJECT))
                .thenReturn(granted);
    }

    private void givenRole(ProjectRole role) {
        when(projectRoleResolver.roleOf(CALLER_ID, PROJECT_ID)).thenReturn(Optional.of(role));
    }
}
