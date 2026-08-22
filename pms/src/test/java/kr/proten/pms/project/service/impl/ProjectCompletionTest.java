package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectFixtures;
import kr.proten.pms.project.service.entity.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 완료 처리·재개 유스케이스 단위 테스트 — AC A7-1~A7-5.
 * 배정 전원에게 열린 경로라(§4 COMPLETE_REOPEN) 권한은 참여자도 통과하고, 대신
 * 상태·진척률 전제와 낙관적 락이 문을 지킨다. 누가 되돌렸는지는 감사 로그가 답한다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectCompletionTest {
    private static final long PROJECT_ID = 7L;
    private static final long ASSIGNEE_ID = 103L;
    private static final long OUTSIDER_ID = 106L;

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
                projectViewFactory);
    }

    @Test
    @DisplayName("A7-1 — 진행중·100%·배정 인원이면 완료로 전이하고 이력을 남긴다")
    void complete_inProgressAtHundred_commitsAndRecordsAudit() {
        // Given
        Project project = givenVisible(ProjectStatus.IN_PROGRESS, 100);
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        // When
        service.complete(ASSIGNEE_ID, PROJECT_ID, 3L);

        // Then
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        verify(projectAuditRecorder).changed(eq(ASSIGNEE_ID), eq(project), any());
    }

    @Test
    @DisplayName("A7-2 — 진척률 100 미만은 409 PROGRESS_INCOMPLETE, 저장하지 않는다")
    void complete_belowHundred_isConflictWithoutSave() {
        // Given
        givenVisible(ProjectStatus.IN_PROGRESS, 90);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.complete(ASSIGNEE_ID, PROJECT_ID, 3L))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.PROGRESS_INCOMPLETE));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A7-4 — 진행중이 아니면 409 INVALID_TRANSITION")
    void complete_notInProgress_isConflict() {
        // Given
        givenVisible(ProjectStatus.ORDER_CONFIRMED, 100);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.complete(ASSIGNEE_ID, PROJECT_ID, 3L))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_TRANSITION));
    }

    @Test
    @DisplayName("A7-3 — 재개는 진행중·진척률 90으로 돌아가고 이력이 남는다")
    void reopen_completed_returnsToInProgressAtNinety() {
        // Given
        Project project = givenVisible(ProjectStatus.COMPLETED, 100);
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        // When
        service.reopen(ASSIGNEE_ID, PROJECT_ID, 3L);

        // Then
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(project.getProgress()).isEqualTo(90);
        verify(projectAuditRecorder).changed(eq(ASSIGNEE_ID), eq(project), any());
    }

    @Test
    @DisplayName("A7-4 — 유지보수중에서는 재개할 수 없다 (409, 계약 정합 보호)")
    void reopen_underMaintenance_isConflict() {
        // Given
        givenVisible(ProjectStatus.UNDER_MAINTENANCE, 100);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.reopen(ASSIGNEE_ID, PROJECT_ID, 3L))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_TRANSITION));
    }

    @Test
    @DisplayName("A7-5 — 가시성 안·미배정은 403 (권한 판정은 상태 전제보다 앞이다)")
    void complete_visibleButUnassigned_isForbidden() {
        // Given
        givenVisible(ProjectStatus.IN_PROGRESS, 100);
        doThrow(new ForbiddenException("담당자만 가능"))
                .when(projectActionPermission)
                .require(ASSIGNEE_ID, PROJECT_ID, ProjectAction.COMPLETE_REOPEN);

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.complete(ASSIGNEE_ID, PROJECT_ID, 3L));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A7-5 — 가시성 밖은 404 은닉")
    void complete_outsideVisibility_throwsNotFound() {
        // Given
        when(projectVisibilityService.requireVisible(OUTSIDER_ID, PROJECT_ID))
                .thenThrow(new NotFoundException());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.complete(OUTSIDER_ID, PROJECT_ID, 3L));
    }

    @Test
    @DisplayName("A2-6 — version 불일치는 409 STALE_VERSION, 전이하지 않는다")
    void complete_staleVersion_isConflict() {
        // Given
        Project project = givenVisible(ProjectStatus.IN_PROGRESS, 100);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.complete(ASSIGNEE_ID, PROJECT_ID, 1L))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.STALE_VERSION));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    private Project givenVisible(ProjectStatus status, int progress) {
        Project project = ProjectFixtures.project(
                PROJECT_ID, "(주)가온아이", "포털 재구축", 13L, status, progress, 3L);
        when(projectVisibilityService.requireVisible(ASSIGNEE_ID, PROJECT_ID)).thenReturn(project);

        return project;
    }
}
