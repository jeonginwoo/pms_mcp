package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;
import kr.proten.pms.common.audit.service.AuditAction;
import kr.proten.pms.common.audit.service.AuditTrail;
import kr.proten.pms.common.audit.service.dto.AuditEntry;
import kr.proten.pms.project.service.entity.Project;
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
 * 감사 기록 판정 단위 테스트 — EPIC G의 세 규칙이 여기 모인다.
 * 바뀐 필드만 남기는가 · status가 바뀐 변경만 STATE_CHANGE인가(§5 전용) ·
 * 프로젝트 스코프 행에 projectId가 채워지는가(G2-1).
 */
@ExtendWith(MockitoExtension.class)
class ProjectAuditRecorderTest {
    private static final long PROJECT_ID = 7L;
    private static final long ACTOR_ID = 103L;

    @Mock
    private AuditTrail auditTrail;

    private ProjectAuditRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new ProjectAuditRecorder(auditTrail);
    }

    @Test
    @DisplayName("A1-1 — 생성은 before 없이 전체 스냅샷 1건이다")
    void created_recordsFullSnapshotWithoutBefore() {
        // Given
        Project project = inProgress(30);

        // When
        recorder.created(ACTOR_ID, project);

        // Then
        AuditEntry entry = captureEntry();
        assertThat(entry.action()).isEqualTo(AuditAction.CREATE);
        assertThat(entry.entityType()).isEqualTo("Project");
        assertThat(entry.entityId()).isEqualTo(PROJECT_ID);
        assertThat(entry.projectId()).isEqualTo(PROJECT_ID);
        assertThat(entry.actorId()).isEqualTo(ACTOR_ID);
        assertThat(entry.before()).isNull();
        assertThat(entry.after()).containsEntry("name", "포털 재구축")
                .containsEntry("status", ProjectStatus.IN_PROGRESS)
                .containsEntry("progress", 30);
    }

    @Test
    @DisplayName("A2-2 — 진척률만 바뀌면 UPDATE이고 바뀐 필드만 담긴다")
    void changed_progressOnly_recordsUpdateWithChangedFieldOnly() {
        // Given
        Project project = inProgress(90);
        Map<String, Object> before = recorder.snapshot(project);
        project.updateProgress(95);

        // When
        recorder.changed(ACTOR_ID, project, before);

        // Then
        AuditEntry entry = captureEntry();
        assertThat(entry.action()).isEqualTo(AuditAction.UPDATE);
        assertThat(entry.before()).containsExactly(Map.entry("progress", 90));
        assertThat(entry.after()).containsExactly(Map.entry("progress", 95));
    }

    @Test
    @DisplayName("A7-1 — status가 바뀐 변경은 STATE_CHANGE다 (§5 전이 전용)")
    void changed_statusChanged_recordsStateChange() {
        // Given
        Project project = inProgress(100);
        Map<String, Object> before = recorder.snapshot(project);
        project.complete();

        // When
        recorder.changed(ACTOR_ID, project, before);

        // Then
        AuditEntry entry = captureEntry();
        assertThat(entry.action()).isEqualTo(AuditAction.STATE_CHANGE);
        assertThat(entry.before()).containsEntry("status", ProjectStatus.IN_PROGRESS);
        assertThat(entry.after()).containsEntry("status", ProjectStatus.COMPLETED);
    }

    @Test
    @DisplayName("A7-3 — 재개는 상태와 진척률을 함께 담는다")
    void changed_reopen_recordsStatusAndProgress() {
        // Given
        Project project = ProjectFixtures.project(
                PROJECT_ID, "(주)가온아이", "포털 재구축", 13L, ProjectStatus.COMPLETED, 100, 5L);
        Map<String, Object> before = recorder.snapshot(project);
        project.reopen();

        // When
        recorder.changed(ACTOR_ID, project, before);

        // Then
        AuditEntry entry = captureEntry();
        assertThat(entry.action()).isEqualTo(AuditAction.STATE_CHANGE);
        assertThat(entry.after()).containsEntry("status", ProjectStatus.IN_PROGRESS)
                .containsEntry("progress", 90);
    }

    @Test
    @DisplayName("G1-1 — 바뀐 것이 없으면 이력도 남지 않는다")
    void changed_nothingChanged_recordsNothing() {
        // Given
        Project project = inProgress(90);

        // When
        recorder.changed(ACTOR_ID, project, recorder.snapshot(project));

        // Then
        verifyNoInteractions(auditTrail);
    }

    @Test
    @DisplayName("G2-1 — 배정 이력도 projectId를 채운다 (entityId는 배정 id다)")
    void assignmentCreated_fillsProjectIdForFiltering() {
        // Given
        ProjectAssignment assignment =
                ProjectFixtures.assignment(31L, PROJECT_ID, 105L, ProjectRole.PARTICIPANT);

        // When
        recorder.assignmentCreated(ACTOR_ID, assignment);

        // Then
        AuditEntry entry = captureEntry();
        assertThat(entry.entityType()).isEqualTo("ProjectAssignment");
        assertThat(entry.entityId()).isEqualTo(31L);
        assertThat(entry.projectId()).isEqualTo(PROJECT_ID);
        assertThat(entry.action()).isEqualTo(AuditAction.CREATE);
        assertThat(entry.after()).containsEntry("personId", 105L)
                .containsEntry("role", ProjectRole.PARTICIPANT);
    }

    @Test
    @DisplayName("B2-1 — 배정 종료는 DELETE로 남고 종료 상태를 담는다")
    void assignmentClosed_recordsDeleteWithClosedStatus() {
        // Given
        ProjectAssignment assignment =
                ProjectFixtures.assignment(31L, PROJECT_ID, 105L, ProjectRole.PARTICIPANT);
        Map<String, Object> before = recorder.snapshot(assignment);
        assignment.close();

        // When
        recorder.assignmentClosed(ACTOR_ID, assignment, before);

        // Then
        AuditEntry entry = captureEntry();
        assertThat(entry.action()).isEqualTo(AuditAction.DELETE);
        assertThat(entry.after()).containsKey("status");
    }

    @Test
    @DisplayName("B1-4 — 배정 수정은 상태가 안 바뀌므로 UPDATE다")
    void assignmentChanged_recordsUpdate() {
        // Given
        ProjectAssignment assignment =
                ProjectFixtures.assignment(31L, PROJECT_ID, 105L, ProjectRole.PARTICIPANT);
        Map<String, Object> before = recorder.snapshot(assignment);
        assignment.reschedule(ProjectFixtures.START, ProjectFixtures.END, 0.9);

        // When
        recorder.assignmentChanged(ACTOR_ID, assignment, before);

        // Then
        AuditEntry entry = captureEntry();
        assertThat(entry.action()).isEqualTo(AuditAction.UPDATE);
        assertThat(entry.after()).containsExactly(Map.entry("monthlyMm", 0.9));
    }

    private Project inProgress(int progress) {
        return ProjectFixtures.project(
                PROJECT_ID, "(주)가온아이", "포털 재구축", 13L,
                ProjectStatus.IN_PROGRESS, progress, 3L);
    }

    private AuditEntry captureEntry() {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditTrail).record(captor.capture());

        return captor.getValue();
    }
}
