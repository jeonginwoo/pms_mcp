package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.person.service.PersonDirectoryService;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
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
 * PM 교체 단위 테스트 — AC A6-1·A6-2·A6-4·A6-5.
 * 불변식(프로젝트당 PM 1행 · managerId 일치)이 한 트랜잭션에서 지켜지는지가 핵심이다:
 * 새 PM 승격 · 직전 PM 강등 · managerId 동기화가 함께 일어나야 한다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectRoleServiceImplTest {
    private static final long PROJECT_ID = 7L;
    private static final long CURRENT_PM_ID = 13L;
    private static final long ASSIGNED_MEMBER_ID = 103L;
    private static final long UNASSIGNED_PERSON_ID = 105L;

    @Mock
    private ProjectRepository projectRepository;
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

    private ProjectRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProjectRoleServiceImpl(
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
    @DisplayName("A6-1·A6-4 — 배정된 인원을 PM으로 올리고 직전 PM은 참여자로 내린다")
    void changeManager_assignedPerson_movesRoleAndSyncsManagerId() {
        // Given
        Project project = givenVisible();
        givenKnownPerson(ASSIGNED_MEMBER_ID);
        ProjectAssignment currentPm = ProjectFixtures.assignment(
                1L, PROJECT_ID, CURRENT_PM_ID, ProjectRole.PM);
        ProjectAssignment member = ProjectFixtures.assignment(
                2L, PROJECT_ID, ASSIGNED_MEMBER_ID, ProjectRole.PARTICIPANT);
        givenCurrentManagerRow(currentPm);
        when(assignmentRepository.findByProjectIdAndPersonIdAndStatus(
                PROJECT_ID, ASSIGNED_MEMBER_ID, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        // When
        service.changeManager(CURRENT_PM_ID, PROJECT_ID, ASSIGNED_MEMBER_ID, 3L);

        // Then
        assertThat(member.getRole()).isEqualTo(ProjectRole.PM);
        assertThat(currentPm.getRole()).isEqualTo(ProjectRole.PARTICIPANT);
        assertThat(project.getManagerId()).isEqualTo(ASSIGNED_MEMBER_ID);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A6-4 — 미배정 인원을 PM으로 지정하면 배정을 함께 만든다 (M/M 0·프로젝트 기간)")
    void changeManager_unassignedPerson_createsAssignment() {
        // Given
        Project project = givenVisible();
        givenKnownPerson(UNASSIGNED_PERSON_ID);
        givenCurrentManagerRow(
                ProjectFixtures.assignment(1L, PROJECT_ID, CURRENT_PM_ID, ProjectRole.PM));
        when(assignmentRepository.findByProjectIdAndPersonIdAndStatus(
                PROJECT_ID, UNASSIGNED_PERSON_ID, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        // When
        service.changeManager(CURRENT_PM_ID, PROJECT_ID, UNASSIGNED_PERSON_ID, 3L);

        // Then
        ArgumentCaptor<ProjectAssignment> captor =
                ArgumentCaptor.forClass(ProjectAssignment.class);
        verify(assignmentRepository).save(captor.capture());
        assertThat(captor.getValue().getPersonId()).isEqualTo(UNASSIGNED_PERSON_ID);
        assertThat(captor.getValue().getRole()).isEqualTo(ProjectRole.PM);
        assertThat(captor.getValue().getMonthlyMm()).isZero();
        assertThat(captor.getValue().getStartDate()).isEqualTo(ProjectFixtures.START);
        assertThat(project.getManagerId()).isEqualTo(UNASSIGNED_PERSON_ID);
    }

    @Test
    @DisplayName("A6-2 — 배정 권한(PM)이 없으면 403")
    void changeManager_withoutAssignPermission_isForbidden() {
        // Given
        givenVisible();
        doThrow(new ForbiddenException("담당자만 가능"))
                .when(projectActionPermission)
                .require(CURRENT_PM_ID, PROJECT_ID, ProjectAction.ASSIGN);

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.changeManager(
                        CURRENT_PM_ID, PROJECT_ID, ASSIGNED_MEMBER_ID, 3L));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A1-3 — 없는 인원을 PM으로 지정하면 422 REF_NOT_FOUND")
    void changeManager_unknownPerson_isUnprocessable() {
        // Given
        givenVisible();
        when(personDirectoryService.existsActive(UNASSIGNED_PERSON_ID)).thenReturn(false);

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.changeManager(
                        CURRENT_PM_ID, PROJECT_ID, UNASSIGNED_PERSON_ID, 3L))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo("REF_NOT_FOUND"));
    }

    @Test
    @DisplayName("이미 PM인 사람을 다시 지정하면 422 — 바뀌는 것이 없는 요청이다")
    void changeManager_samePerson_isUnprocessable() {
        // Given
        givenVisible();
        givenKnownPerson(CURRENT_PM_ID);

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.changeManager(
                        CURRENT_PM_ID, PROJECT_ID, CURRENT_PM_ID, 3L))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo("INVALID_ROLE"));
    }

    @Test
    @DisplayName("A8-7 — version 불일치는 409 STALE_VERSION, 역할을 건드리지 않는다")
    void changeManager_staleVersion_isConflict() {
        // Given
        Project project = givenVisible();
        givenKnownPerson(ASSIGNED_MEMBER_ID);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.changeManager(
                        CURRENT_PM_ID, PROJECT_ID, ASSIGNED_MEMBER_ID, 1L))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo("STALE_VERSION"));
        assertThat(project.getManagerId()).isEqualTo(CURRENT_PM_ID);
    }

    private Project givenVisible() {
        Project project = ProjectFixtures.project(
                PROJECT_ID, "(주)가온아이", "포털 재구축", CURRENT_PM_ID,
                ProjectStatus.IN_PROGRESS, 50, 3L);
        when(projectVisibilityService.requireVisible(CURRENT_PM_ID, PROJECT_ID))
                .thenReturn(project);

        return project;
    }

    private void givenKnownPerson(long personId) {
        when(personDirectoryService.existsActive(personId)).thenReturn(true);
    }

    private void givenCurrentManagerRow(ProjectAssignment currentPm) {
        when(assignmentRepository.findByProjectIdAndRoleAndStatus(
                PROJECT_ID, ProjectRole.PM, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(currentPm));
    }
}
