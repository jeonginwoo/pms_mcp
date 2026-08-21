package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.dto.ProgressUpdateResult;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;
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
 * 진척률 갱신 유스케이스 단위 테스트 — AC A2-1~A2-8.
 * 2단계 확인(confirmed=false 요약 → confirmed=true 커밋)·낙관적 락·완료 상태
 * 거절·가시성/권한 의미론(404 은닉 vs 403)이 전부 이 서비스에 모인다.
 * 역할별 판정 표 자체는 {@link ProjectActionPermission}의 테스트가 본다.
 */
@ExtendWith(MockitoExtension.class)
class ProgressUpdateServiceImplTest {
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

    private ProgressUpdateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProgressUpdateServiceImpl(
                projectRepository,
                projectVisibilityService,
                projectActionPermission,
                projectAuditRecorder);
    }

    @Test
    @DisplayName("A2-1 — confirmed=false는 변경 요약만 돌려주고 DB를 건드리지 않는다")
    void update_notConfirmed_returnsSummaryWithoutCommit() {
        // Given
        givenVisibleInProgressProject(90);

        // When
        ProgressUpdateResult result = service.update(
                ASSIGNEE_ID, new UpdateProgressCommand(PROJECT_ID, 95, 3L, false));

        // Then
        assertThat(result.committed()).isFalse();
        assertThat(result.currentProgress()).isEqualTo(90);
        assertThat(result.requestedProgress()).isEqualTo(95);
        verify(projectRepository, never()).saveAndFlush(any());
        verifyNoInteractions(projectAuditRecorder);
    }

    @Test
    @DisplayName("A2-2 — confirmed=true는 커밋하고 UPDATE 이력을 남긴다")
    void update_confirmed_commitsProgressAndRecordsAudit() {
        // Given
        Project project = givenVisibleInProgressProject(90);
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        // When
        ProgressUpdateResult result = service.update(
                ASSIGNEE_ID, new UpdateProgressCommand(PROJECT_ID, 95, 3L, true));

        // Then
        assertThat(result.committed()).isTrue();
        assertThat(project.getProgress()).isEqualTo(95);
        verify(projectAuditRecorder).changed(eq(ASSIGNEE_ID), eq(project), any());
    }

    @Test
    @DisplayName("A2-3 — 100 저장은 상태를 바꾸지 않고 완료 처리 가능만 알린다")
    void update_hundred_keepsStatusAndReportsCompletable() {
        // Given
        Project project = givenVisibleInProgressProject(90);
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        // When
        ProgressUpdateResult result = service.update(
                ASSIGNEE_ID, new UpdateProgressCommand(PROJECT_ID, 100, 3L, true));

        // Then
        assertThat(result.completable()).isTrue();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("A2-4 — 가시성 밖은 404 은닉 (권한 판정에 도달하지 않는다)")
    void update_outsideVisibility_throwsNotFound() {
        // Given
        when(projectVisibilityService.requireVisible(OUTSIDER_ID, PROJECT_ID))
                .thenThrow(new NotFoundException());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> service.update(
                OUTSIDER_ID, new UpdateProgressCommand(PROJECT_ID, 95, 3L, true)));
        verifyNoInteractions(projectActionPermission);
    }

    @Test
    @DisplayName("A2-4 — 가시성 안이지만 권한 판정에서 걸리면 403")
    void update_visibleButNotPermitted_isForbidden() {
        // Given
        givenVisibleInProgressProject(OUTSIDER_ID, 90);
        doThrow(new ForbiddenException("담당자만 가능"))
                .when(projectActionPermission)
                .require(OUTSIDER_ID, PROJECT_ID, ProjectAction.PROGRESS);

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class).isThrownBy(() -> service.update(
                OUTSIDER_ID, new UpdateProgressCommand(PROJECT_ID, 95, 3L, true)));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A2-7 — 판정을 통과하면 미배정(관리자 치환)이어도 커밋된다")
    void update_permittedWithoutAssignment_commits() {
        // Given
        // 플래그 치환은 역할 해석자·권한 판정이 담당한다 — 여기서는 통과한 결과만 본다
        Project project = givenVisibleInProgressProject(1L, 90);
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        // When
        ProgressUpdateResult result = service.update(
                1L, new UpdateProgressCommand(PROJECT_ID, 95, 3L, true));

        // Then
        assertThat(result.committed()).isTrue();
    }

    @Test
    @DisplayName("A2-5 — 범위 밖 진척률은 400, 프로젝트 조회조차 하지 않는다")
    void update_progressOutOfRange_isValidationError() {
        // When · Then
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> service.update(
                ASSIGNEE_ID, new UpdateProgressCommand(PROJECT_ID, 101, 3L, true)));
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> service.update(
                ASSIGNEE_ID, new UpdateProgressCommand(PROJECT_ID, -1, 3L, true)));
        verify(projectVisibilityService, never()).requireVisible(ASSIGNEE_ID, PROJECT_ID);
    }

    @Test
    @DisplayName("A2-6 — version 불일치는 409 STALE_VERSION, 최신 값을 함께 알린다")
    void update_staleVersion_isConflictWithLatestValues() {
        // Given
        givenVisibleInProgressProject(90);

        // When · Then
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> service.update(
                ASSIGNEE_ID, new UpdateProgressCommand(PROJECT_ID, 95, 1L, true)))
                .satisfies(thrown -> {
                    assertThat(thrown.code()).isEqualTo("STALE_VERSION");
                    assertThat(thrown.getMessage()).contains("90").contains("3");
                });
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A2-6 — version은 확인 단계에서만 검사한다 (요약 요청은 통과)")
    void update_staleVersionButNotConfirmed_returnsSummary() {
        // Given
        givenVisibleInProgressProject(90);

        // When
        ProgressUpdateResult result = service.update(
                ASSIGNEE_ID, new UpdateProgressCommand(PROJECT_ID, 95, 1L, false));

        // Then
        assertThat(result.committed()).isFalse();
    }

    @Test
    @DisplayName("진척률은 진행중에서만 — 계약대기·수주확정은 409 NOT_IN_PROGRESS (2026-08-22)")
    void update_notInProgress_isConflict() {
        // Given
        Project pending = ProjectFixtures.project(
                PROJECT_ID, "(주)가온아이", "포털 재구축", 13L,
                ProjectStatus.CONTRACT_PENDING, 0, 0L);
        when(projectVisibilityService.requireVisible(ASSIGNEE_ID, PROJECT_ID)).thenReturn(pending);

        // When · Then
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> service.update(
                ASSIGNEE_ID, new UpdateProgressCommand(PROJECT_ID, 40, 0L, true)))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo("NOT_IN_PROGRESS"));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A2-8 — 완료 상태의 진척률 직접 수정은 409 PROJECT_COMPLETED")
    void update_completedProject_isConflict() {
        // Given
        Project completed = ProjectFixtures.project(
                PROJECT_ID, "(주)가온아이", "포털 재구축", 13L, ProjectStatus.COMPLETED, 100, 5L);
        when(projectVisibilityService.requireVisible(ASSIGNEE_ID, PROJECT_ID))
                .thenReturn(completed);

        // When · Then
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> service.update(
                ASSIGNEE_ID, new UpdateProgressCommand(PROJECT_ID, 95, 5L, true)))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo("PROJECT_COMPLETED"));
    }

    private Project givenVisibleInProgressProject(int progress) {
        return givenVisibleInProgressProject(ASSIGNEE_ID, progress);
    }

    private Project givenVisibleInProgressProject(long callerPersonId, int progress) {
        Project project = ProjectFixtures.project(
                PROJECT_ID, "(주)가온아이", "포털 재구축", 13L,
                ProjectStatus.IN_PROGRESS, progress, 3L);
        when(projectVisibilityService.requireVisible(callerPersonId, PROJECT_ID))
                .thenReturn(project);

        return project;
    }
}
