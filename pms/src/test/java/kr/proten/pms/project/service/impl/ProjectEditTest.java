package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.entity.Engagement;
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
 * 프로젝트 정보·상태 수정 유스케이스 단위 테스트 — AC A5-1~A5-3.
 * 판정 순서(가시성 404 은닉 → 권한 403 → version 409 → 전이 409 → 중복 409)와
 * "위반 시 아무것도 안 바뀜"이 이 서비스의 계약이다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectEditTest {
    private static final long PROJECT_ID = 7L;
    private static final long LEAD_ID = 104L;
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
    @Mock
    private OrgPermissionService orgPermissionService;

    private ProjectCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProjectCommandServiceImpl(
                projectRepository,
                assignmentRepository,
                projectVisibilityService,
                projectActionPermission,
                personDirectoryService,
                orgPermissionService,
                new AssignmentFactory(),
                projectAuditRecorder,
                projectViewFactory);
    }

    @Test
    @DisplayName("A5-1 — 순방향 전이와 정보 수정이 함께 커밋되고 이력이 남는다")
    void edit_forwardTransition_commitsAndRecordsAudit() {
        // Given
        Project project = givenVisible(ProjectStatus.CONTRACT_PENDING);
        givenNoDuplicate();
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        // When
        service.edit(LEAD_ID, command(ProjectStatus.ORDER_CONFIRMED, "포털 재구축"));

        // Then
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ORDER_CONFIRMED);
        assertThat(project.getSolution()).isEqualTo("AI");
        verify(projectAuditRecorder).changed(eq(LEAD_ID), eq(project), any());
    }

    @Test
    @DisplayName("A5-1 — 상태를 그대로 주면 정보만 바뀐다")
    void edit_sameStatus_updatesInfoOnly() {
        // Given
        Project project = givenVisible(ProjectStatus.IN_PROGRESS);
        givenNoDuplicate();
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        // When
        service.edit(LEAD_ID, command(ProjectStatus.IN_PROGRESS, "이름 변경"));

        // Then
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(project.getName()).isEqualTo("이름 변경");
    }

    @Test
    @DisplayName("A5-2 — 역방향 전이는 409 INVALID_TRANSITION, 저장에 도달하지 않는다")
    void edit_backwardTransition_isConflictWithoutSave() {
        // Given
        givenVisible(ProjectStatus.IN_PROGRESS);
        givenNoDuplicate();

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.edit(
                        LEAD_ID, command(ProjectStatus.CONTRACT_PENDING, "포털 재구축")))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_TRANSITION));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A5-1 — 완료로의 전이는 이 경로에서 막힌다 (전용 경로만)")
    void edit_toCompleted_isConflict() {
        // Given
        givenVisible(ProjectStatus.IN_PROGRESS);
        givenNoDuplicate();

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.edit(
                        LEAD_ID, command(ProjectStatus.COMPLETED, "포털 재구축")));
    }

    @Test
    @DisplayName("A5-3 — 참여자 토큰은 403 (권한 판정이 전이보다 앞이다)")
    void edit_participant_isForbiddenBeforeTransition() {
        // Given
        givenVisible(ProjectStatus.CONTRACT_PENDING);
        doThrow(new ForbiddenException("담당자만 가능"))
                .when(projectActionPermission)
                .require(LEAD_ID, PROJECT_ID, ProjectAction.EDIT_INFO);

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.edit(
                        LEAD_ID, command(ProjectStatus.ORDER_CONFIRMED, "포털 재구축")));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A3-2 — 가시성 밖은 404 은닉 (권한 판정에 도달하지 않는다)")
    void edit_outsideVisibility_throwsNotFound() {
        // Given
        when(projectVisibilityService.requireVisible(OUTSIDER_ID, PROJECT_ID))
                .thenThrow(new NotFoundException());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.edit(
                        OUTSIDER_ID, command(ProjectStatus.ORDER_CONFIRMED, "포털 재구축")));
    }

    @Test
    @DisplayName("A8-7 — version 불일치는 409 STALE_VERSION, 아무것도 안 바뀐다")
    void edit_staleVersion_isConflict() {
        // Given
        Project project = givenVisible(ProjectStatus.CONTRACT_PENDING);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.edit(LEAD_ID, new EditProjectCommand(
                        PROJECT_ID, "(주)가온아이", "포털 재구축", "AI", Engagement.REMOTE, 3.0,
                        ProjectFixtures.START, ProjectFixtures.END,
                        ProjectStatus.ORDER_CONFIRMED, 1L)))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.STALE_VERSION));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CONTRACT_PENDING);
    }

    @Test
    @DisplayName("A1-2 — 같은 고객사·이름이 다른 프로젝트에 있으면 409 DUPLICATE_NAME")
    void edit_duplicateName_isConflict() {
        // Given
        givenVisible(ProjectStatus.CONTRACT_PENDING);
        when(projectRepository
                .existsByNormalizedClientAndNormalizedNameAndDeletedFalseAndIdNot(
                        anyString(), anyString(), anyLong()))
                .thenReturn(true);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.edit(
                        LEAD_ID, command(ProjectStatus.ORDER_CONFIRMED, "겹치는 이름")))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.DUPLICATE_NAME));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    private Project givenVisible(ProjectStatus status) {
        Project project = ProjectFixtures.project(
                PROJECT_ID, "(주)가온아이", "포털 재구축", 13L, status, 50, 3L);
        when(projectVisibilityService.requireVisible(LEAD_ID, PROJECT_ID)).thenReturn(project);

        return project;
    }

    private void givenNoDuplicate() {
        when(projectRepository
                .existsByNormalizedClientAndNormalizedNameAndDeletedFalseAndIdNot(
                        anyString(), anyString(), anyLong()))
                .thenReturn(false);
    }

    private EditProjectCommand command(ProjectStatus status, String name) {
        return new EditProjectCommand(
                PROJECT_ID,
                "(주)가온아이",
                name,
                "AI",
                Engagement.REMOTE,
                3.0,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 3, 31),
                status,
                3L);
    }
}
