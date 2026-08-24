package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import kr.proten.pms.project.ProjectReminderDue;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectFixtures;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 마감 임박·완료 지연 일일 점검 (AC F2-1 · F3-1).
 *
 * <p>이 클래스가 잠그는 것은 <b>누구에게 무엇이 발행되는가</b>다. 적재·멱등은
 * 구독자와 {@code notifications} 유니크 제약의 몫이라 여기서 보지 않는다 —
 * 스케줄러는 알림을 만들지 않고 대상을 찾는다.
 *
 * <p>시계를 고정한다: 두 창(D-7 · 100%인 채 7일)이 전부 "오늘"을 기준으로 재므로
 * 실시간 시계로는 자정에 깨지는 종류의 단정이 된다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectReminderSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-08-25T06:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);

    private static final long PROJECT_ID = 7L;
    private static final long PM_ID = 13L;
    private static final long PL_ID = 21L;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAssignmentRepository assignmentRepository;
    @Mock
    private ApplicationEventPublisher events;

    private ProjectReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        lenient().when(projectRepository.findDeadlineNear(any(), any())).thenReturn(List.of());
        lenient().when(projectRepository.findCompletionOverdue(any())).thenReturn(List.of());
        lenient().when(assignmentRepository.findByProjectIdAndRoleAndStatus(
                anyLong(), any(), any())).thenReturn(List.of());
        scheduler = new ProjectReminderScheduler(projectRepository, assignmentRepository,
                events, Clock.fixed(NOW, ZONE));
    }

    @Test
    @DisplayName("F2-1 — 마감 창은 오늘부터 D+7까지다 (N=7)")
    void deadlineWindowIsSevenDaysAhead() {
        // When
        scheduler.sweep();

        // Then
        ArgumentCaptor<LocalDate> through = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        verify(projectRepository).findDeadlineNear(from.capture(), through.capture());
        // 하한이 오늘이다 — 이미 지난 마감은 "임박"이 아니다(2026-08-25 정정)
        assertThat(from.getValue()).isEqualTo(TODAY);
        assertThat(through.getValue()).isEqualTo(TODAY.plusDays(7));
    }

    @Test
    @DisplayName("F2-1 — 마감 임박은 PM 한 사람에게만 간다 (배정 전원이 아니다)")
    void deadlineNearTargetsOnlyTheManager() {
        // Given
        Project project = project(ProjectStatus.IN_PROGRESS, 60, TODAY.plusDays(3));
        when(projectRepository.findDeadlineNear(any(), any())).thenReturn(List.of(project));

        // When
        scheduler.sweep();

        // Then
        ProjectReminderDue event = captureKind(ProjectReminderDue.Kind.DEADLINE_NEAR);
        assertThat(event.projectId()).isEqualTo(PROJECT_ID);
        assertThat(event.dueDate()).isEqualTo(TODAY.plusDays(3));
        assertThat(event.runDate()).isEqualTo(TODAY);
        assertThat(event.recipientIds()).containsExactly(PM_ID);
    }

    @Test
    @DisplayName("F3-1 — 완료 지연 기준은 100% 도달 후 7일이다")
    void overdueWindowIsSevenDaysBack() {
        // When
        scheduler.sweep();

        // Then
        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(projectRepository).findCompletionOverdue(since.capture());
        assertThat(since.getValue()).isEqualTo(NOW.minusSeconds(7 * 24 * 3600));
    }

    @Test
    @DisplayName("F3-1 — 완료 지연은 PM·PL에게 가고 참여자는 빠진다")
    void completionOverdueTargetsLeadsOnly() {
        // Given
        Project project = project(ProjectStatus.IN_PROGRESS, 100, TODAY.plusMonths(1));
        ReflectionTestUtils.setField(project, "hundredReachedAt",
                NOW.minusSeconds(10 * 24 * 3600));
        when(projectRepository.findCompletionOverdue(any())).thenReturn(List.of(project));
        when(assignmentRepository.findByProjectIdAndRoleAndStatus(
                PROJECT_ID, ProjectRole.PL, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(ProjectFixtures.assignment(1L, PROJECT_ID, PL_ID,
                        ProjectRole.PL)));

        // When
        scheduler.sweep();

        // Then — 참여자는 조회 자체를 하지 않는다(역할로 걸러 온다)
        ProjectReminderDue event = captureKind(ProjectReminderDue.Kind.COMPLETION_OVERDUE);
        assertThat(event.recipientIds()).containsExactlyInAnyOrder(PM_ID, PL_ID);
    }

    @Test
    @DisplayName("F3-1 — PM이 PL 배정도 갖고 있으면 한 번만 센다")
    void leadsAreDistinct() {
        // Given
        Project project = project(ProjectStatus.IN_PROGRESS, 100, null);
        ReflectionTestUtils.setField(project, "hundredReachedAt",
                NOW.minusSeconds(10 * 24 * 3600));
        when(projectRepository.findCompletionOverdue(any())).thenReturn(List.of(project));
        when(assignmentRepository.findByProjectIdAndRoleAndStatus(
                PROJECT_ID, ProjectRole.PL, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(ProjectFixtures.assignment(2L, PROJECT_ID, PM_ID,
                        ProjectRole.PL)));

        // When
        scheduler.sweep();

        // Then
        assertThat(captureKind(ProjectReminderDue.Kind.COMPLETION_OVERDUE).recipientIds())
                .containsExactly(PM_ID);
    }

    @Test
    @DisplayName("F3-1 — 도달일이 문구 재료로 실린다 (구독자가 되묻지 않는다)")
    void overdueCarriesTheReachedDate() {
        // Given
        Project project = project(ProjectStatus.IN_PROGRESS, 100, null);
        ReflectionTestUtils.setField(project, "hundredReachedAt",
                Instant.parse("2026-08-10T03:00:00Z"));
        when(projectRepository.findCompletionOverdue(any())).thenReturn(List.of(project));

        // When
        scheduler.sweep();

        // Then
        assertThat(captureKind(ProjectReminderDue.Kind.COMPLETION_OVERDUE).dueDate())
                .isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    @DisplayName("F2·F3 — 대상이 없으면 아무것도 발행하지 않는다")
    void publishesNothingWhenNothingIsDue() {
        // When
        scheduler.sweep();

        // Then
        verify(events, never()).publishEvent(any(ProjectReminderDue.class));
    }

    private ProjectReminderDue captureKind(ProjectReminderDue.Kind kind) {
        ArgumentCaptor<ProjectReminderDue> event =
                ArgumentCaptor.forClass(ProjectReminderDue.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().kind()).isEqualTo(kind);

        return event.getValue();
    }

    private static Project project(ProjectStatus status, int progress, LocalDate endDate) {
        Project project = ProjectFixtures.project(
                PROJECT_ID, "명화공업", "MES 구축", PM_ID, status, progress, 0L);
        ReflectionTestUtils.setField(project, "endDate", endDate);

        return project;
    }
}
