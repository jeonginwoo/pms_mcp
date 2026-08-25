package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.project.repository.ProjectPermissionOverrideRepository;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectPermissionOverride;
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
 * 진척률과 완료·재개는 배정 전원(A2-1·A7-1).
 *
 * 2026-08-26(US-A8)부터 프로젝트별 override 케이스가 그 위에 붙는다 — A8-5(축소)·
 * A8-6(확장)·고정 칸 무시. 병합기는 실물을 쓰고 저장소만 목이라, 기본값은 여전히
 * §4-2 표에서 나온다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectActionPermissionTest {
    private static final long PROJECT_ID = 7L;
    private static final long CALLER_ID = 103L;

    @Mock
    private ProjectRoleResolver projectRoleResolver;
    @Mock
    private OrgPermissionService orgPermissionService;
    /*
     * 병합기는 목이 아니라 **실물**이고 저장소만 목이다 — `allows`를 목으로 흉내 내면
     * 이 테스트가 §4-2 표 대신 제 스텁을 검사하게 된다(항진명제).
     */
    @Mock
    private ProjectPermissionOverrideRepository overrideRepository;

    private ProjectActionPermission permission;

    @BeforeEach
    void setUp() {
        permission = new ProjectActionPermission(projectRoleResolver, orgPermissionService,
                new ProjectPermissionMatrixResolver(overrideRepository));
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

    @Test
    @DisplayName("A8-5 — PROGRESS를 끈 프로젝트의 참여자는 403 (판정이 매트릭스를 참조한다)")
    void require_progressTurnedOffForParticipant_isForbidden() {
        givenOverrides(ProjectPermissionOverride.of(
                PROJECT_ID, ProjectRole.PARTICIPANT, ProjectAction.PROGRESS, false));

        givenRole(ProjectRole.PARTICIPANT);
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> require(ProjectAction.PROGRESS));

        // 같은 프로젝트에서 PL은 그대로 통과한다 — 끈 것은 참여자 칸 하나다
        givenRole(ProjectRole.PL);
        assertThatNoException().isThrownBy(() -> require(ProjectAction.PROGRESS));
    }

    @Test
    @DisplayName("A8-6 — ASSIGN을 PL로 확장한 프로젝트의 PL은 통과 (기본값은 PM만)")
    void require_assignExtendedToLead_allowsLead() {
        givenOverrides(ProjectPermissionOverride.of(
                PROJECT_ID, ProjectRole.PL, ProjectAction.ASSIGN, true));

        givenRole(ProjectRole.PL);
        assertThatNoException().isThrownBy(() -> require(ProjectAction.ASSIGN));

        // 확장은 PL 칸만이다 — 참여자는 여전히 403
        givenRole(ProjectRole.PARTICIPANT);
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> require(ProjectAction.ASSIGN));
    }

    @Test
    @DisplayName("§4-2 고정 — PM 칸을 끈 행이 DB에 있어도 판정은 무시한다")
    void require_overrideOnFixedCell_isIgnored() {
        // 저장 경로는 422로 막지만(A8-4) DB를 직접 고친 행이 판정을 뚫으면 안 된다
        givenOverrides(ProjectPermissionOverride.of(
                PROJECT_ID, ProjectRole.PM, ProjectAction.PROGRESS, false));

        givenRole(ProjectRole.PM);
        assertThatNoException().isThrownBy(() -> require(ProjectAction.PROGRESS));
    }

    private void require(ProjectAction action) {
        permission.require(CALLER_ID, PROJECT_ID, action);
    }

    private void givenOverrides(ProjectPermissionOverride... overrides) {
        when(overrideRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(overrides));
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
