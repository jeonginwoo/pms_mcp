package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.proten.pms.person.service.dto.OrgPermission;
import kr.proten.pms.person.service.OrgPermissionService;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.service.entity.ProjectFixtures;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 프로젝트 역할 해석 단위 테스트 (상위 PRD §4-1).
 * 역할의 정본은 배정 레코드이고, 유일한 예외가 "전 프로젝트 관리" 플래그 치환
 * (모든 프로젝트에서 PM 간주)이다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectRoleResolverTest {
    private static final long PROJECT_ID = 7L;

    @Mock
    private ProjectAssignmentRepository assignmentRepository;
    @Mock
    private OrgPermissionService orgPermissionService;

    private ProjectRoleResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ProjectRoleResolver(assignmentRepository, orgPermissionService);
    }

    @Test
    @DisplayName("배정된 역할이 정본이다")
    void roleOf_assigned_returnsAssignmentRole() {
        // Given
        when(orgPermissionService.has(103L, OrgPermission.MANAGE_ALL_PROJECTS)).thenReturn(false);
        when(assignmentRepository.findByProjectIdAndPersonIdAndStatus(
                PROJECT_ID, 103L, AssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(ProjectFixtures.assignment(
                        1L, PROJECT_ID, 103L, ProjectRole.PL)));

        // When · Then
        assertThat(resolver.roleOf(103L, PROJECT_ID)).contains(ProjectRole.PL);
    }

    @Test
    @DisplayName("미배정이면 역할이 없다 — 403의 근거")
    void roleOf_unassigned_isEmpty() {
        // Given
        when(orgPermissionService.has(106L, OrgPermission.MANAGE_ALL_PROJECTS)).thenReturn(false);
        when(assignmentRepository.findByProjectIdAndPersonIdAndStatus(
                PROJECT_ID, 106L, AssignmentStatus.ACTIVE)).thenReturn(Optional.empty());

        // When · Then
        assertThat(resolver.roleOf(106L, PROJECT_ID)).isEmpty();
    }

    @Test
    @DisplayName("전 프로젝트 관리 플래그 — 배정을 보지 않고 PM으로 간주한다 (§4-1 치환)")
    void roleOf_manageAllProjectsFlag_substitutesPm() {
        // Given
        when(orgPermissionService.has(1L, OrgPermission.MANAGE_ALL_PROJECTS)).thenReturn(true);

        // When · Then
        assertThat(resolver.roleOf(1L, PROJECT_ID)).contains(ProjectRole.PM);
        verify(assignmentRepository, never()).findByProjectIdAndPersonIdAndStatus(
                PROJECT_ID, 1L, AssignmentStatus.ACTIVE);
    }
}
