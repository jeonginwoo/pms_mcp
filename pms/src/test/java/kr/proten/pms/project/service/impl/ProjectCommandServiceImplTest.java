package kr.proten.pms.project.service.impl;

import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.service.entity.ProjectFixtures;
import kr.proten.pms.project.service.entity.ProjectRole;
import kr.proten.pms.project.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 프로젝트 생성 유스케이스 단위 테스트 — AC A1-1~A1-6.
 * 생성 권한은 권한 그룹 플래그가 판정하고(프로젝트 역할은 판정 축이 아니다),
 * PM 1행 불변식과 인원 참조 검증이 이 계층에 있다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectCommandServiceImplTest {
    private static final long TEAM_LEAD_ID = 102L;
    private static final long PM_ID = 13L;
    private static final long MEMBER_ID = 103L;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAssignmentRepository assignmentRepository;
    @Mock
    private PersonDirectoryService personDirectoryService;
    @Mock
    private OrgPermissionService orgPermissionService;
    @Mock
    private ProjectAuditRecorder projectAuditRecorder;
    @Mock
    private ProjectViewFactory projectViewFactory;
    @Mock
    private ProjectVisibilityService projectVisibilityService;
    @Mock
    private ProjectActionPermission projectActionPermission;

    private ProjectCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        // 배정 생성기는 순수 변환이라 실물을 쓴다 — 기본값 채움(A1-4)까지 함께 검증된다
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
    @DisplayName("A1-1·A1-4 — 유효 입력이면 계약대기로 생성되고 지정 역할로 배정된다")
    void create_validCommand_savesPendingProjectWithAssignments() {
        // Given
        givenCreatePermission(true);
        givenKnownPeople(PM_ID, MEMBER_ID);
        givenNoDuplicate();
        givenSaveEchoesArgument();

        // When
        service.create(TEAM_LEAD_ID, command(
                new AssignmentSpec(PM_ID, ProjectRole.PM, null, null, 0.5),
                new AssignmentSpec(MEMBER_ID, ProjectRole.PARTICIPANT, null, null, 0.7)));

        // Then
        Project saved = captureSavedProject();
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.CONTRACT_PENDING);
        assertThat(saved.getProgress()).isZero();
        assertThat(saved.getManagerId()).isEqualTo(PM_ID);
        assertThat(captureSavedAssignments()).map(ProjectAssignment::getRole)
                .containsExactly(ProjectRole.PM, ProjectRole.PARTICIPANT);
    }

    @Test
    @DisplayName("A1-4 — 배정 기간 미지정은 프로젝트 기간으로 채운다")
    void create_assignmentWithoutPeriod_fallsBackToProjectPeriod() {
        // Given
        givenCreatePermission(true);
        givenKnownPeople(PM_ID);
        givenNoDuplicate();
        givenSaveEchoesArgument();

        // When
        service.create(TEAM_LEAD_ID, command(
                new AssignmentSpec(PM_ID, ProjectRole.PM, null, null, 0.0)));

        // Then
        ProjectAssignment assignment = captureSavedAssignments().getFirst();
        assertThat(assignment.getStartDate()).isEqualTo(ProjectFixtures.START);
        assertThat(assignment.getEndDate()).isEqualTo(ProjectFixtures.END);
    }

    @Test
    @DisplayName("A1-5 — 프로젝트 생성 플래그가 없으면 403, 아무것도 저장하지 않는다")
    void create_withoutCreateProjectFlag_isForbidden() {
        // Given
        givenCreatePermission(MEMBER_ID, false);

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class).isThrownBy(() ->
                service.create(MEMBER_ID, command(
                        new AssignmentSpec(PM_ID, ProjectRole.PM, null, null, 0.5))));
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("A1-2 — 같은 고객사·같은 이름(정규화 후)은 409 DUPLICATE_NAME")
    void create_duplicateNormalizedName_isConflict() {
        // Given
        givenCreatePermission(true);
        givenKnownPeople(PM_ID);
        when(projectRepository.existsByNormalizedClientAndNormalizedNameAndDeletedFalse(
                "(주)가온아이", "포털 재구축")).thenReturn(true);

        // When · Then
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() ->
                service.create(TEAM_LEAD_ID, command(
                        new AssignmentSpec(PM_ID, ProjectRole.PM, null, null, 0.5))))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.DUPLICATE_NAME));
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("A1-4 — PM 미지정은 422 PM_REQUIRED")
    void create_withoutPm_isUnprocessable() {
        // Given
        givenCreatePermission(true);

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class).isThrownBy(() ->
                service.create(TEAM_LEAD_ID, command(
                        new AssignmentSpec(MEMBER_ID, ProjectRole.PARTICIPANT, null, null, 0.5))))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.PM_REQUIRED));
    }

    @Test
    @DisplayName("A1-6 — PM이 2행 이상이면 422 MULTIPLE_PM")
    void create_multiplePm_isUnprocessable() {
        // Given
        givenCreatePermission(true);

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class).isThrownBy(() ->
                service.create(TEAM_LEAD_ID, command(
                        new AssignmentSpec(PM_ID, ProjectRole.PM, null, null, 0.5),
                        new AssignmentSpec(MEMBER_ID, ProjectRole.PM, null, null, 0.5))))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.MULTIPLE_PM));
    }

    @Test
    @DisplayName("PL 복수는 정상 입력이다 — 구 A1-7 제약 해제 (2026-08-06 확정)")
    void create_multiplePl_isAccepted() {
        // Given
        givenCreatePermission(true);
        givenKnownPeople(PM_ID, MEMBER_ID, 104L);
        givenNoDuplicate();
        givenSaveEchoesArgument();

        // When
        service.create(TEAM_LEAD_ID, command(
                new AssignmentSpec(PM_ID, ProjectRole.PM, null, null, 0.5),
                new AssignmentSpec(MEMBER_ID, ProjectRole.PL, null, null, 0.5),
                new AssignmentSpec(104L, ProjectRole.PL, null, null, 0.5)));

        // Then
        assertThat(captureSavedAssignments()).map(ProjectAssignment::getRole)
                .containsExactly(ProjectRole.PM, ProjectRole.PL, ProjectRole.PL);
    }

    @Test
    @DisplayName("A1-3 — 없는 인원 id는 422 REF_NOT_FOUND")
    void create_unknownPerson_isUnprocessable() {
        // Given
        givenCreatePermission(true);
        when(personDirectoryService.existsActive(PM_ID)).thenReturn(false);

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class).isThrownBy(() ->
                service.create(TEAM_LEAD_ID, command(
                        new AssignmentSpec(PM_ID, ProjectRole.PM, null, null, 0.5))))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.REF_NOT_FOUND));
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("A1-1 — 생성 이력은 CREATE 1건이다 (배정별로 나누지 않는다)")
    void create_recordsSingleCreateAudit() {
        // Given
        givenCreatePermission(true);
        givenKnownPeople(PM_ID, MEMBER_ID);
        givenNoDuplicate();
        givenSaveEchoesArgument();

        // When
        service.create(TEAM_LEAD_ID, command(
                new AssignmentSpec(PM_ID, ProjectRole.PM, null, null, 0.5),
                new AssignmentSpec(MEMBER_ID, ProjectRole.PARTICIPANT, null, null, 0.5)));

        // Then
        verify(projectAuditRecorder).created(eq(TEAM_LEAD_ID), any(Project.class));
    }

    private CreateProjectCommand command(AssignmentSpec... assignments) {
        return new CreateProjectCommand(
                "(주)가온아이",
                "포털 재구축",
                "검색엔진",
                Engagement.REMOTE,
                2.0,
                ProjectFixtures.START,
                ProjectFixtures.END,
                List.of(assignments));
    }

    private void givenCreatePermission(boolean granted) {
        givenCreatePermission(TEAM_LEAD_ID, granted);
    }

    private void givenCreatePermission(long callerPersonId, boolean granted) {
        when(orgPermissionService.has(callerPersonId, OrgPermission.CREATE_PROJECT))
                .thenReturn(granted);
    }

    private void givenKnownPeople(long... personIds) {
        for (long personId : personIds) {
            when(personDirectoryService.existsActive(personId)).thenReturn(true);
        }
    }

    private void givenNoDuplicate() {
        when(projectRepository.existsByNormalizedClientAndNormalizedNameAndDeletedFalse(
                anyString(), anyString())).thenReturn(false);
    }

    /** 저장 결과 표현은 ProjectViewFactory의 몫이므로 여기서는 저장 인자만 되돌려준다. */
    private void givenSaveEchoesArgument() {
        when(projectRepository.save(any(Project.class))).thenAnswer(call -> call.getArgument(0));
        when(assignmentRepository.saveAll(anyCollection()))
                .thenAnswer(call -> List.copyOf(call.getArgument(0)));
    }

    private Project captureSavedProject() {
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());

        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<ProjectAssignment> captureSavedAssignments() {
        ArgumentCaptor<List<ProjectAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(assignmentRepository).saveAll(captor.capture());

        return captor.getValue();
    }
}
