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
import java.util.Optional;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.service.dto.CreateAssignmentCommand;
import kr.proten.pms.project.service.dto.UpdateAssignmentCommand;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.service.entity.ProjectFixtures;
import kr.proten.pms.project.service.entity.ProjectRole;
import kr.proten.pms.project.service.entity.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 인력 배정 유스케이스 단위 테스트 — AC B1-1·B1-2·B1-4·B2-1.
 *
 * 배정 id로 들어오는 경로(수정·종료)도 프로젝트 가시성을 먼저 통과해야 한다 —
 * 배정 존재 여부만으로 응답이 갈리면 가시성 밖 프로젝트의 배정을 헤아릴 수 있다.
 * 배정 생성기는 순수 변환이라 실물을 쓴다(기본값 채움까지 함께 검증된다).
 */
@ExtendWith(MockitoExtension.class)
class AssignmentServiceImplTest {
    private static final long PROJECT_ID = 7L;
    private static final long ASSIGNMENT_ID = 31L;
    private static final long PM_ID = 13L;
    private static final long NEW_MEMBER_ID = 105L;

    @Mock
    private ProjectAssignmentRepository assignmentRepository;
    @Mock
    private ProjectVisibilityService projectVisibilityService;
    @Mock
    private ProjectActionPermission projectActionPermission;
    @Mock
    private PersonDirectoryService personDirectoryService;
    @Mock
    private ProjectAuditRecorder projectAuditRecorder;
    @Mock
    private ProjectViewFactory projectViewFactory;

    private AssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AssignmentServiceImpl(
                assignmentRepository,
                projectVisibilityService,
                projectActionPermission,
                personDirectoryService,
                new AssignmentFactory(),
                projectAuditRecorder,
                projectViewFactory);
    }

    @Test
    @DisplayName("B1-1 — PM이 배정하면 201 대상이 저장되고 이력이 남는다")
    void assign_byManager_savesAssignmentAndRecordsAudit() {
        // Given
        givenVisibleProject();
        givenKnownPerson();
        givenNoActiveAssignment();
        ProjectAssignment saved = activeAssignment(ProjectRole.PARTICIPANT);
        when(assignmentRepository.save(any())).thenReturn(saved);

        // When
        service.assign(PM_ID, createCommand(ProjectRole.PARTICIPANT));

        // Then
        verify(projectAuditRecorder).assignmentCreated(PM_ID, saved);
    }

    @Test
    @DisplayName("B1-1 — 기간 미지정은 프로젝트 기간으로 채워진다 (A6-6 기본값)")
    void assign_withoutPeriod_fillsProjectPeriod() {
        // Given
        givenVisibleProject();
        givenKnownPerson();
        givenNoActiveAssignment();
        when(assignmentRepository.save(any())).thenReturn(activeAssignment(ProjectRole.PL));

        // When
        service.assign(PM_ID, new CreateAssignmentCommand(
                PROJECT_ID, NEW_MEMBER_ID, ProjectRole.PL, null, null, 0.3));

        // Then
        ProjectAssignment created = captureCreated();
        assertThat(created.getStartDate()).isEqualTo(ProjectFixtures.START);
        assertThat(created.getEndDate()).isEqualTo(ProjectFixtures.END);
        assertThat(created.getMonthlyMm()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("B1-2 — 종료 안 된 같은 인원의 배정이 있으면 409 DUPLICATE_ASSIGNMENT")
    void assign_alreadyAssigned_isConflict() {
        // Given
        givenVisibleProject();
        givenKnownPerson();
        when(assignmentRepository.existsByProjectIdAndPersonIdAndStatus(
                PROJECT_ID, NEW_MEMBER_ID, AssignmentStatus.ACTIVE)).thenReturn(true);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.assign(PM_ID, createCommand(ProjectRole.PARTICIPANT)))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.DUPLICATE_ASSIGNMENT));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A6-7 — role=PM은 이 경로로 만들 수 없다 (422 INVALID_ROLE)")
    void assign_managerRole_isUnprocessable() {
        // Given
        givenVisibleProject();

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.assign(PM_ID, createCommand(ProjectRole.PM)))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_ROLE));
    }

    @Test
    @DisplayName("A1-3 — 없는 인원은 422 REF_NOT_FOUND")
    void assign_unknownPerson_isUnprocessable() {
        // Given
        givenVisibleProject();
        when(personDirectoryService.existsActive(NEW_MEMBER_ID)).thenReturn(false);

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.assign(PM_ID, createCommand(ProjectRole.PARTICIPANT)))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.REF_NOT_FOUND));
    }

    @Test
    @DisplayName("B1-4 — PL·참여자 토큰은 403 (배정은 PM의 일)")
    void assign_byNonManager_isForbidden() {
        // Given
        givenVisibleProject();
        doThrow(new ForbiddenException("담당자만 가능"))
                .when(projectActionPermission)
                .require(PM_ID, PROJECT_ID, ProjectAction.ASSIGN);

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.assign(PM_ID, createCommand(ProjectRole.PARTICIPANT)));
    }

    @Test
    @DisplayName("A3-2 — 가시성 밖 프로젝트에는 배정할 수 없다 (404 은닉)")
    void assign_outsideVisibility_throwsNotFound() {
        // Given
        when(projectVisibilityService.requireVisible(PM_ID, PROJECT_ID))
                .thenThrow(new NotFoundException());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.assign(PM_ID, createCommand(ProjectRole.PARTICIPANT)));
    }

    @Test
    @DisplayName("B1-4 — 기간·투입 M/M을 수정하고 UPDATE 이력을 남긴다")
    void update_changesPeriodAndMm() {
        // Given
        ProjectAssignment assignment = givenExistingAssignment(ProjectRole.PARTICIPANT);
        when(assignmentRepository.saveAndFlush(assignment)).thenReturn(assignment);

        // When
        service.update(PM_ID, new UpdateAssignmentCommand(ASSIGNMENT_ID,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30), 0.9, 0L));

        // Then
        assertThat(assignment.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(assignment.getMonthlyMm()).isEqualTo(0.9);
        verify(projectAuditRecorder).assignmentChanged(eq(PM_ID), eq(assignment), any());
    }

    @Test
    @DisplayName("B1-4 — version 불일치는 409 STALE_VERSION, 저장하지 않는다")
    void update_staleVersion_isConflict() {
        // Given
        givenExistingAssignment(ProjectRole.PARTICIPANT, 4L);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.update(PM_ID, new UpdateAssignmentCommand(
                        ASSIGNMENT_ID, null, null, 0.9, 1L)))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.STALE_VERSION));
        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("없는 배정 id는 404 — 가시성 밖과 같은 응답이다")
    void update_unknownAssignment_throwsNotFound() {
        // Given
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.empty());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.update(PM_ID, new UpdateAssignmentCommand(
                        ASSIGNMENT_ID, null, null, 0.9, 0L)));
    }

    @Test
    @DisplayName("B2-1 — 종료하면 상태가 종료로 바뀌고 DELETE 이력이 남는다")
    void close_marksClosedAndRecordsDelete() {
        // Given
        ProjectAssignment assignment = givenExistingAssignment(ProjectRole.PARTICIPANT);
        when(assignmentRepository.saveAndFlush(assignment)).thenReturn(assignment);

        // When
        service.close(PM_ID, ASSIGNMENT_ID);

        // Then
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.CLOSED);
        verify(projectAuditRecorder).assignmentClosed(eq(PM_ID), eq(assignment), any());
    }

    @Test
    @DisplayName("A6-5 — PM 배정은 종료할 수 없다 (422 INVALID_ROLE)")
    void close_managerAssignment_isUnprocessable() {
        // Given
        givenExistingAssignment(ProjectRole.PM);

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.close(PM_ID, ASSIGNMENT_ID))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_ROLE));
        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    private Project givenVisibleProject() {
        Project project = ProjectFixtures.project(
                PROJECT_ID, "(주)가온아이", "포털 재구축", PM_ID,
                ProjectStatus.IN_PROGRESS, 50, 3L);
        when(projectVisibilityService.requireVisible(PM_ID, PROJECT_ID)).thenReturn(project);

        return project;
    }

    private ProjectAssignment givenExistingAssignment(ProjectRole role) {
        return givenExistingAssignment(role, 0L);
    }

    private ProjectAssignment givenExistingAssignment(ProjectRole role, long version) {
        ProjectAssignment assignment = ProjectFixtures.assignment(
                ASSIGNMENT_ID, PROJECT_ID, NEW_MEMBER_ID, role, version);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        givenVisibleProject();

        return assignment;
    }

    private ProjectAssignment activeAssignment(ProjectRole role) {
        return ProjectFixtures.assignment(ASSIGNMENT_ID, PROJECT_ID, NEW_MEMBER_ID, role);
    }

    private void givenKnownPerson() {
        when(personDirectoryService.existsActive(NEW_MEMBER_ID)).thenReturn(true);
    }

    private void givenNoActiveAssignment() {
        when(assignmentRepository.existsByProjectIdAndPersonIdAndStatus(
                anyLong(), anyLong(), any())).thenReturn(false);
    }

    private CreateAssignmentCommand createCommand(ProjectRole role) {
        return new CreateAssignmentCommand(PROJECT_ID, NEW_MEMBER_ID, role,
                ProjectFixtures.START, ProjectFixtures.END, 0.5);
    }

    private ProjectAssignment captureCreated() {
        ArgumentCaptor<ProjectAssignment> captor =
                ArgumentCaptor.forClass(ProjectAssignment.class);
        verify(assignmentRepository).save(captor.capture());

        return captor.getValue();
    }
}
