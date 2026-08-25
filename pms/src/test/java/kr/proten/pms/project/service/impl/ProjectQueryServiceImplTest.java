package kr.proten.pms.project.service.impl;

import kr.proten.pms.project.service.dto.AssignmentView;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.ProjectSummary;
import kr.proten.pms.project.service.dto.ProjectVisibility;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import kr.proten.pms.audit.AuditQueryService;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.service.entity.ProjectFixtures;
import kr.proten.pms.project.service.entity.ProjectPhase;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 프로젝트 조회 유스케이스 단위 테스트 — AC A3-1~A3-3.
 * 목록은 가시 프로젝트 id로 좁힌 질의여야 하고(전체 로드 후 필터 금지), 단건은
 * 가시성 밖을 404로 은닉한다. 상세의 배정 레코드는 타 팀 인원까지 노출한다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectQueryServiceImplTest {
    private static final long PROJECT_ID = 7L;
    private static final long TEAM_LEAD_ID = 102L;
    private static final long PM_ID = 13L;
    private static final long OTHER_TEAM_MEMBER_ID = 106L;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAssignmentRepository assignmentRepository;
    @Mock
    private ProjectVisibilityService projectVisibilityService;
    @Mock
    private AuditQueryService auditQueryService;
    @Mock
    private PersonDirectoryService personDirectoryService;

    private ProjectQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProjectQueryServiceImpl(
                projectRepository,
                projectVisibilityService,
                new ProjectViewFactory(assignmentRepository, personDirectoryService),
                auditQueryService);
    }

    @Test
    @DisplayName("A3-1 — 제한 가시성은 가시 프로젝트 id로 좁혀 페이지 질의한다")
    void listVisible_restricted_queriesVisibleIdsOnly() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        Project project = ProjectFixtures.project(
                PROJECT_ID, "(주)가온아이", "포털 재구축", PM_ID,
                ProjectStatus.IN_PROGRESS, 90, 3L);
        when(projectVisibilityService.visibilityOf(TEAM_LEAD_ID))
                .thenReturn(ProjectVisibility.of(Set.of(PROJECT_ID)));
        when(projectRepository.findByIdInAndDeletedFalse(Set.of(PROJECT_ID), pageable))
                .thenReturn(new PageImpl<>(List.of(project), pageable, 1));
        when(personDirectoryService.findRefs(anyCollection()))
                .thenReturn(List.of(
                        new PersonRef(PM_ID, "이피엠", "SI팀", "SI부문", "책임", true)));

        // When
        var page = service.listVisible(TEAM_LEAD_ID, null, pageable);

        // Then
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().managerName()).isEqualTo("이피엠");
        verify(projectRepository, never()).findByDeletedFalse(any());
    }

    @Test
    @DisplayName("A3-1 — 가시 프로젝트가 없으면 질의 없이 빈 페이지")
    void listVisible_nothingVisible_returnsEmptyPageWithoutQuery() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        when(projectVisibilityService.visibilityOf(TEAM_LEAD_ID))
                .thenReturn(ProjectVisibility.of(Set.of()));

        // When
        var page = service.listVisible(TEAM_LEAD_ID, null, pageable);

        // Then
        assertThat(page).isEmpty();
        verify(projectRepository, never()).findByIdInAndDeletedFalse(anyCollection(), any());
    }

    @Test
    @DisplayName("A3-1 — 전사 가시성은 전체 페이지를 질의한다")
    void listVisible_unrestricted_queriesAllProjects() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        when(projectVisibilityService.visibilityOf(1L))
                .thenReturn(ProjectVisibility.all());
        when(projectRepository.findByDeletedFalse(pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // When
        service.listVisible(1L, null, pageable);

        // Then
        verify(projectRepository, never()).findByIdInAndDeletedFalse(anyCollection(), any());
    }

    @Test
    @DisplayName("A3-1 ?phase= — 전사 가시성은 그룹의 상태 집합으로 좁혀 질의한다")
    void listVisible_unrestrictedWithPhase_queriesStatusSet() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        when(projectVisibilityService.visibilityOf(1L)).thenReturn(ProjectVisibility.all());
        when(projectRepository.findByDeletedFalseAndStatusIn(
                ProjectPhase.SALES.statuses(), pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // When
        service.listVisible(1L, ProjectPhase.SALES, pageable);

        // Then — 무필터 질의로 새지 않는다(전체를 받아 메모리에서 거르면 안 된다)
        verify(projectRepository, never()).findByDeletedFalse(any());
    }

    @Test
    @DisplayName("A3-1 ?phase= — 제한 가시성은 id 집합과 상태 집합을 함께 건다")
    void listVisible_restrictedWithPhase_queriesBothIdsAndStatuses() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        when(projectVisibilityService.visibilityOf(TEAM_LEAD_ID))
                .thenReturn(ProjectVisibility.of(Set.of(PROJECT_ID)));
        when(projectRepository.findByIdInAndDeletedFalseAndStatusIn(
                Set.of(PROJECT_ID), ProjectPhase.SOLUTION.statuses(), pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // When
        service.listVisible(TEAM_LEAD_ID, ProjectPhase.SOLUTION, pageable);

        // Then — 필터가 가시성을 대체하지 않는다: id 조건이 빠진 질의는 부르지 않는다
        verify(projectRepository, never()).findByDeletedFalseAndStatusIn(anyCollection(), any());
        verify(projectRepository, never()).findByIdInAndDeletedFalse(anyCollection(), any());
    }

    @Test
    @DisplayName("A3-1 ?phase= — 가시 프로젝트가 없으면 필터가 있어도 질의하지 않는다")
    void listVisible_nothingVisibleWithPhase_returnsEmptyPageWithoutQuery() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        when(projectVisibilityService.visibilityOf(TEAM_LEAD_ID))
                .thenReturn(ProjectVisibility.of(Set.of()));

        // When
        var page = service.listVisible(TEAM_LEAD_ID, ProjectPhase.SALES, pageable);

        // Then
        assertThat(page).isEmpty();
        verify(projectRepository, never())
                .findByIdInAndDeletedFalseAndStatusIn(anyCollection(), anyCollection(), any());
    }

    /*
     * 기대 표를 여기 손으로 적는 것이 이 테스트의 전부다 — 구현에서 파생한 값끼리
     * 대조하면(예: `of(s)`가 준 phase의 `statuses()`가 s를 담는지) 항진명제가 되어
     * §5 문면과의 어긋남을 잡지 못한다. 상태가 늘면 `of`의 switch가 컴파일을 깨고
     * 이 표가 테스트를 깨서, 두 곳이 함께 결정을 요구한다.
     */
    @Test
    @DisplayName("§5 — phase 표는 §5 문면과 같고 두 그룹이 유지보수중을 뺀 전부를 나눈다")
    void projectPhase_tableMatchesTheSpec() {
        // §5: 영업={계약대기,수주확정} · 솔루션={진행중,완료} · 유지보수중은 어디에도 없다
        assertThat(ProjectPhase.SALES.statuses())
                .containsExactlyInAnyOrder(
                        ProjectStatus.CONTRACT_PENDING, ProjectStatus.ORDER_CONFIRMED);
        assertThat(ProjectPhase.SOLUTION.statuses())
                .containsExactlyInAnyOrder(ProjectStatus.IN_PROGRESS, ProjectStatus.COMPLETED);
        assertThat(ProjectPhase.of(ProjectStatus.UNDER_MAINTENANCE)).isNull();

        // 두 그룹이 겹치지 않고, 둘의 합집합 + 유지보수중이 상태 전부다 —
        // 새 상태가 조용히 "어느 탭에도 없음"으로 떨어지면 여기서 걸린다
        assertThat(ProjectPhase.SALES.statuses())
                .doesNotContainAnyElementsOf(ProjectPhase.SOLUTION.statuses());
        Set<ProjectStatus> grouped = EnumSet.copyOf(ProjectPhase.SALES.statuses());
        grouped.addAll(ProjectPhase.SOLUTION.statuses());
        grouped.add(ProjectStatus.UNDER_MAINTENANCE);
        assertThat(grouped).containsExactlyInAnyOrder(ProjectStatus.values());

        // 양방향이 서로 뒤집힌다 — status → phase와 phase → 집합이 같은 표를 본다
        for (ProjectPhase phase : ProjectPhase.values()) {
            for (ProjectStatus status : phase.statuses()) {
                assertThat(ProjectPhase.of(status)).isEqualTo(phase);
            }
        }
    }

    @Test
    @DisplayName("A3-2 — 가시성 밖 단건은 404 은닉")
    void getProject_outsideVisibility_throwsNotFound() {
        // Given
        when(projectVisibilityService.requireVisible(TEAM_LEAD_ID, PROJECT_ID))
                .thenThrow(new NotFoundException());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getProject(TEAM_LEAD_ID, PROJECT_ID));
    }

    @Test
    @DisplayName("A3-3 — 배정된 프로젝트 상세는 타 팀 인원 배정 레코드까지 노출한다")
    void getProject_assigned_exposesEveryAssignment() {
        // Given
        Project project = ProjectFixtures.project(
                PROJECT_ID, "(주)가온아이", "포털 재구축", PM_ID,
                ProjectStatus.IN_PROGRESS, 90, 3L);
        when(projectVisibilityService.requireVisible(OTHER_TEAM_MEMBER_ID, PROJECT_ID))
                .thenReturn(project);
        when(assignmentRepository.findByProjectIdAndStatus(PROJECT_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(
                        ProjectFixtures.assignment(1L, PROJECT_ID, PM_ID, ProjectRole.PM),
                        ProjectFixtures.assignment(
                                2L, PROJECT_ID, OTHER_TEAM_MEMBER_ID, ProjectRole.PARTICIPANT)));
        when(personDirectoryService.findRefs(anyCollection())).thenReturn(List.of(
                new PersonRef(PM_ID, "이피엠", "SI팀", "SI부문", "책임", true),
                new PersonRef(OTHER_TEAM_MEMBER_ID, "타부문원", "AX사업기획부", "AX사업기획부",
                        "주임", true)));

        // When
        ProjectDetail detail = service.getProject(OTHER_TEAM_MEMBER_ID, PROJECT_ID);

        // Then
        assertThat(detail.assignments()).map(AssignmentView::personName)
                .containsExactly("이피엠", "타부문원");
        assertThat(detail.assignments()).map(AssignmentView::role)
                .containsExactly(ProjectRole.PM, ProjectRole.PARTICIPANT);
    }

    @Test
    @DisplayName("A3-3 — 배정 레코드가 없어도 상세는 조회된다")
    void getProject_withoutAssignments_returnsDetail() {
        // Given
        Project project = ProjectFixtures.project(PROJECT_ID, "(주)가온아이", "포털 재구축", PM_ID);
        when(projectVisibilityService.requireVisible(TEAM_LEAD_ID, PROJECT_ID))
                .thenReturn(project);
        when(assignmentRepository.findByProjectIdAndStatus(PROJECT_ID, AssignmentStatus.ACTIVE))
                .thenReturn(List.<ProjectAssignment>of());

        // When
        ProjectDetail detail = service.getProject(TEAM_LEAD_ID, PROJECT_ID);

        // Then
        assertThat(detail.assignments()).isEmpty();
        assertThat(detail.status()).isEqualTo(ProjectStatus.CONTRACT_PENDING);
    }
}
