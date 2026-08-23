package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectFixtures;
import kr.proten.pms.project.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 프로젝트 삭제 유스케이스 단위 테스트 — AC A4-1·A4-2.
 * 권한 규칙 자체(PM 또는 생성 권한자 — 2026-08-22 결정)는 ProjectActionPermission의
 * 테스트가 보고, 여기서는 순서(404 은닉 → 403)와 soft 삭제·이력만 본다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectDeleteTest {
    private static final long PROJECT_ID = 7L;
    private static final long PM_ID = 13L;
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
    @DisplayName("A4-1 — soft 삭제되고 DELETE 이력이 남는다")
    void delete_marksDeletedAndRecordsAudit() {
        // Given
        Project project = givenVisible();
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        // When
        service.delete(PM_ID, PROJECT_ID);

        // Then
        assertThat(project.isDeleted()).isTrue();
        verify(projectAuditRecorder).deleted(eq(PM_ID), eq(project), any());
    }

    @Test
    @DisplayName("A4-2 — 권한이 없으면 403, 아무것도 안 바뀐다")
    void delete_withoutPermission_isForbidden() {
        // Given
        Project project = givenVisible();
        doThrow(new ForbiddenException("담당자만 가능"))
                .when(projectActionPermission).requireDelete(PM_ID, PROJECT_ID);

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.delete(PM_ID, PROJECT_ID));
        assertThat(project.isDeleted()).isFalse();
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A3-2 — 가시성 밖은 404 은닉 (권한 판정에 도달하지 않는다)")
    void delete_outsideVisibility_throwsNotFound() {
        // Given
        when(projectVisibilityService.requireVisible(OUTSIDER_ID, PROJECT_ID))
                .thenThrow(new NotFoundException());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.delete(OUTSIDER_ID, PROJECT_ID));
    }

    private Project givenVisible() {
        Project project = ProjectFixtures.project(
                PROJECT_ID, "(주)가온아이", "포털 재구축", PM_ID, ProjectStatus.IN_PROGRESS, 50, 3L);
        when(projectVisibilityService.requireVisible(PM_ID, PROJECT_ID)).thenReturn(project);

        return project;
    }
}
