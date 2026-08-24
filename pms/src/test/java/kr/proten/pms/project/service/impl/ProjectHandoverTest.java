package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.PersonDirectoryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import kr.proten.pms.project.HandoverPort;
import kr.proten.pms.project.HandoverSpec;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 유지보수 이관 유스케이스 단위 테스트 — AC D1-1·D1-2·D1-3.
 *
 * <p>이 클래스가 잠그는 것은 <b>순서</b>다. D1-2("아무것도 안 바뀜")와 D1-3("상태 전이도
 * 미발생")은 둘 다 "무엇이 일어나지 <b>않았는지</b>"를 요구하므로, 단정의 절반이
 * {@code never()}와 "상태가 그대로다"다. 계약이 실제로 만들어지는지는 포트 구현
 * ({@code HandoverAdapterTest})과 통합 테스트가 본다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectHandoverTest {
    /** 시계를 고정한다 — 100% 도달 시각이 단정에 들어온다(F3-1). */
    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private static final long PROJECT_ID = 7L;
    private static final long MANAGER_ID = 13L;
    private static final long OUTSIDER_ID = 106L;
    private static final long VERSION = 3L;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectVisibilityService projectVisibilityService;
    @Mock
    private ProjectActionPermission projectActionPermission;
    @Mock
    private ProjectAuditRecorder projectAuditRecorder;
    @Mock
    private ProjectViewFactory projectViewFactory;
    @Mock
    private HandoverPort handoverPort;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private ProjectAssignmentRepository assignmentRepository;
    @Mock
    private PersonDirectoryService personDirectoryService;

    private ProjectLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProjectLifecycleServiceImpl(
                projectRepository,
                assignmentRepository,
                projectVisibilityService,
                projectActionPermission,
                personDirectoryService,
                new AssignmentFactory(),
                projectAuditRecorder,
                projectViewFactory,
                handoverPort,
                Clock.fixed(NOW, ZoneId.systemDefault()),
                events);
    }

    @Test
    @DisplayName("D1-1 — 완료 프로젝트를 이관하면 유지보수중으로 전이하고 계약을 만든다")
    void handover_completed_transitionsAndCreatesContract() {
        // Given
        Project project = givenVisible(ProjectStatus.COMPLETED);
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        // When
        service.handover(MANAGER_ID, PROJECT_ID, spec(), VERSION);

        // Then
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.UNDER_MAINTENANCE);
        verify(handoverPort).createHandoverContract(MANAGER_ID, PROJECT_ID, spec());
        // 상태가 바뀌었으므로 STATE_CHANGE다 (§5 "모든 전이")
        verify(projectAuditRecorder).changed(eq(MANAGER_ID), eq(project), any());
    }

    @Test
    @DisplayName("D1-1 — 이관 권한은 HANDOVER 칸이다 (완료·재개와 다른 판정)")
    void handover_usesItsOwnPermissionCell() {
        // Given
        Project project = givenVisible(ProjectStatus.COMPLETED);
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        // When
        service.handover(MANAGER_ID, PROJECT_ID, spec(), VERSION);

        // Then — COMPLETE_REOPEN은 배정 전원이라 이관에 쓰면 PL·참여자도 통과한다
        verify(projectActionPermission)
                .require(MANAGER_ID, PROJECT_ID, ProjectAction.HANDOVER);
    }

    @Test
    @DisplayName("D1-2 — 완료가 아니면 409이고 계약을 만들지 않는다 (아무것도 안 바뀜)")
    void handover_notCompleted_isConflictAndCreatesNothing() {
        // Given
        Project project = givenVisible(ProjectStatus.IN_PROGRESS);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.handover(MANAGER_ID, PROJECT_ID, spec(), VERSION))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_TRANSITION));
        // 이 두 줄이 D1-2의 "아무것도 안 바뀜"이다
        verify(handoverPort, never()).createHandoverContract(anyLong(), anyLong(), any());
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("D1-2 — 이미 유지보수중인 프로젝트는 다시 이관할 수 없다")
    void handover_alreadyUnderMaintenance_isConflict() {
        // Given
        givenVisible(ProjectStatus.UNDER_MAINTENANCE);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.handover(MANAGER_ID, PROJECT_ID, spec(), VERSION))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_TRANSITION));
        verify(handoverPort, never()).createHandoverContract(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("D1-3 — 계약 필수 정보가 모자라면 400이고 상태 전이도 일어나지 않는다")
    void handover_invalidSpec_leavesTheProjectCompleted() {
        // Given — 포트 구현이 필수값을 보고 400을 던진다
        Project project = givenVisible(ProjectStatus.COMPLETED);
        doThrow(new ValidationException("사이트를 1개 이상 등록해야 합니다", "sites"))
                .when(handoverPort).createHandoverContract(anyLong(), anyLong(), any());

        // When · Then
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.handover(MANAGER_ID, PROJECT_ID, spec(), VERSION));
        // 검증이 전이보다 앞이라는 것이 이 단정이다 (D1-3)
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        verify(projectRepository, never()).saveAndFlush(any());
        verify(projectAuditRecorder, never()).changed(anyLong(), any(), any());
    }

    @Test
    @DisplayName("D1-2 — version 불일치는 409이고 계약을 만들지 않는다")
    void handover_staleVersion_isConflictBeforeTheContract() {
        // Given
        Project project = givenVisible(ProjectStatus.COMPLETED);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.handover(MANAGER_ID, PROJECT_ID, spec(), 1L))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.STALE_VERSION));
        verify(handoverPort, never()).createHandoverContract(anyLong(), anyLong(), any());
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
    }

    @Test
    @DisplayName("D1-1 — PM이 아니면 403이고 계약을 만들지 않는다")
    void handover_withoutPermission_isForbiddenBeforeTheContract() {
        // Given
        givenVisible(ProjectStatus.COMPLETED);
        doThrow(new ForbiddenException("담당자만 가능")).when(projectActionPermission)
                .require(MANAGER_ID, PROJECT_ID, ProjectAction.HANDOVER);

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.handover(MANAGER_ID, PROJECT_ID, spec(), VERSION));
        verify(handoverPort, never()).createHandoverContract(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("D1-1 — 가시성 밖은 404로 은닉한다 (403보다 앞선다)")
    void handover_outsideVisibility_isNotFound() {
        // Given
        when(projectVisibilityService.requireVisible(OUTSIDER_ID, PROJECT_ID))
                .thenThrow(new NotFoundException());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.handover(OUTSIDER_ID, PROJECT_ID, spec(), VERSION));
        verify(handoverPort, never()).createHandoverContract(anyLong(), anyLong(), any());
    }

    private Project givenVisible(ProjectStatus status) {
        Project project = ProjectFixtures.project(
                PROJECT_ID, "명화공업", "MES 구축", MANAGER_ID, status, 100, VERSION);
        when(projectVisibilityService.requireVisible(MANAGER_ID, PROJECT_ID))
                .thenReturn(project);

        return project;
    }

    /** 시연 앵커 = 명화공업 (부록 B). */
    private static HandoverSpec spec() {
        return new HandoverSpec("명화공업", "MES 유지보수", LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31), 24000000L, 2000000L,
                List.of(new HandoverSpec.Site("명화공업 본사", 26L)));
    }
}
