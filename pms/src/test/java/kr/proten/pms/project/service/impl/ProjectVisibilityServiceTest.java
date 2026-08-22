package kr.proten.pms.project.service.impl;

import kr.proten.pms.project.service.dto.ProjectVisibility;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.OrgVisibility;
import kr.proten.pms.person.OrgVisibilityService;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 프로젝트 가시성 판정 단위 테스트 (상위 PRD §4-4).
 * 조직 가시성(사람 기준)을 프로젝트로 옮기는 규칙 — 가시 인원이 한 명이라도
 * 배정된 프로젝트가 보이고, 본인 배정 프로젝트는 조직 밖이어도 보인다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectVisibilityServiceTest {
    private static final long PROJECT_ID = 7L;
    private static final long TEAM_LEAD_ID = 102L;

    @Mock
    private OrgVisibilityService orgVisibilityService;
    @Mock
    private ProjectAssignmentRepository assignmentRepository;
    @Mock
    private ProjectRepository projectRepository;

    private ProjectVisibilityService service;

    @BeforeEach
    void setUp() {
        service = new ProjectVisibilityService(
                orgVisibilityService, assignmentRepository, projectRepository);
    }

    @Test
    @DisplayName("전사 가시성 — 배정 질의 없이 제약 없음으로 접힌다")
    void visibilityOf_unrestricted_skipsAssignmentQuery() {
        // Given
        when(orgVisibilityService.visibilityOf(1L)).thenReturn(OrgVisibility.unrestricted(1L));

        // When
        ProjectVisibility visibility = service.visibilityOf(1L);

        // Then
        assertThat(visibility.unrestricted()).isTrue();
        verify(assignmentRepository, never()).findDistinctProjectIdsByPersonIds(
                anyCollection(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("제한 가시성 — 가시 인원이 배정된 프로젝트 id로 접힌다")
    void visibilityOf_restricted_collectsProjectsOfVisiblePeople() {
        // Given
        when(orgVisibilityService.visibilityOf(TEAM_LEAD_ID))
                .thenReturn(OrgVisibility.of(TEAM_LEAD_ID, Set.of(103L)));
        when(assignmentRepository.findDistinctProjectIdsByPersonIds(
                List.of(TEAM_LEAD_ID, 103L), AssignmentStatus.ACTIVE))
                .thenReturn(List.of(PROJECT_ID, 9L));

        // When
        ProjectVisibility visibility = service.visibilityOf(TEAM_LEAD_ID);

        // Then
        assertThat(visibility.unrestricted()).isFalse();
        assertThat(visibility.visibleProjectIds()).containsExactlyInAnyOrder(PROJECT_ID, 9L);
        assertThat(visibility.canView(PROJECT_ID)).isTrue();
        assertThat(visibility.canView(99L)).isFalse();
    }

    @Test
    @DisplayName("단건 — 존재하고 가시성 안이면 엔티티를 돌려준다")
    void requireVisible_visible_returnsProject() {
        // Given
        Project project = ProjectFixtures.project(PROJECT_ID, "(주)가온아이", "포털 재구축", 13L);
        when(projectRepository.findByIdAndDeletedFalse(PROJECT_ID))
                .thenReturn(Optional.of(project));
        when(orgVisibilityService.visibilityOf(TEAM_LEAD_ID))
                .thenReturn(OrgVisibility.unrestricted(TEAM_LEAD_ID));

        // When
        Project found = service.requireVisible(TEAM_LEAD_ID, PROJECT_ID);

        // Then
        assertThat(found).isSameAs(project);
    }

    @Test
    @DisplayName("단건 — 부재(soft 삭제 포함)와 가시성 밖은 같은 404")
    void requireVisible_absentOrInvisible_throwsSameNotFound() {
        // Given
        when(projectRepository.findByIdAndDeletedFalse(900L)).thenReturn(Optional.empty());
        when(projectRepository.findByIdAndDeletedFalse(PROJECT_ID)).thenReturn(
                Optional.of(ProjectFixtures.project(PROJECT_ID, "(주)가온아이", "포털 재구축", 13L)));
        when(orgVisibilityService.visibilityOf(TEAM_LEAD_ID))
                .thenReturn(OrgVisibility.of(TEAM_LEAD_ID, Set.of()));
        when(assignmentRepository.findDistinctProjectIdsByPersonIds(
                List.of(TEAM_LEAD_ID), AssignmentStatus.ACTIVE)).thenReturn(List.of());

        // When · Then — 두 경로가 같은 예외로 수렴해야 은닉이 성립한다
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.requireVisible(TEAM_LEAD_ID, 900L));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.requireVisible(TEAM_LEAD_ID, PROJECT_ID));
    }
}
