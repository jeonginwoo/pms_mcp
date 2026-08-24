package kr.proten.pms.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.notification.NotificationService;
import kr.proten.pms.notification.NotificationType;
import kr.proten.pms.notification.NotifyCommand;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.ProjectLifecycleChanged;
import kr.proten.pms.project.ProjectReminderDue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 리마인더·생애주기 구독 (AC F2-1·F2-2 · F3-1·F3-2·F3-3).
 *
 * <p><b>이 클래스가 없어서 실제 결함이 통과했다</b>(2026-08-25 리뷰): 스케줄러 테스트가
 * "적재·멱등은 구독자의 몫이라 여기서 보지 않는다"고 위임했는데 <b>위임받는 쪽에
 * 테스트가 없었다</b>. 그 사이로 "마감 임박 멱등 키가 종료일이라 종료일당 평생 1건"이
 * 빠져나갔고, 주석 네 곳은 "매일 다시 알린다"고 적고 있었다.
 *
 * <p>그래서 여기가 잠그는 것은 <b>멱등 키</b>다 — 유형·문구보다 그쪽이 규칙이다.
 */
@ExtendWith(MockitoExtension.class)
class ReminderSubscriberTest {
    private static final long PROJECT_ID = 7L;
    private static final long PM_ID = 13L;
    private static final long PL_ID = 21L;
    private static final LocalDate DUE = LocalDate.of(2026, 9, 1);
    private static final LocalDate RUN = LocalDate.of(2026, 8, 25);

    @Mock
    private NotificationService notificationService;
    @Mock
    private OrgPermissionService orgPermissionService;
    @Mock
    private PersonDirectoryService personDirectoryService;

    private NotificationSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new NotificationSubscriber(
                notificationService, orgPermissionService, personDirectoryService);
    }

    @Test
    @DisplayName("F2-2 — 마감 임박의 멱등 키는 점검이 돈 날이다 (종료일이 아니다)")
    void deadlineKeyCarriesTheRunDateSoItRemindsDaily() {
        // When — 같은 프로젝트를 이틀에 걸쳐 점검한다
        subscriber.onReminderDue(deadline(RUN));
        subscriber.onReminderDue(deadline(RUN.plusDays(1)));

        // Then — 키가 달라야 이튿날에도 알림이 생긴다.
        //        종료일을 키에 쓰면 두 키가 같아져 "일일 점검"이 성립하지 않는다
        assertThat(keysOf(2))
                .containsExactly("deadline:7:2026-08-25", "deadline:7:2026-08-26");
    }

    @Test
    @DisplayName("F2-1 — 마감 임박은 DEADLINE_NEAR 유형이고 문구에 종료일이 든다")
    void deadlineCarriesTypeAndEndDate() {
        // When
        subscriber.onReminderDue(deadline(RUN));

        // Then
        NotifyCommand command = capture();
        assertThat(command.type()).isEqualTo(NotificationType.DEADLINE_NEAR);
        assertThat(command.recipientId()).isEqualTo(PM_ID);
        assertThat(command.refType()).isEqualTo("Project");
        assertThat(command.refId()).isEqualTo(PROJECT_ID);
        assertThat(command.message()).contains("2026-09-01");
    }

    @Test
    @DisplayName("F3-2 — 완료 지연의 멱등 키는 도달일이다 (매일 다시 알리지 않는다)")
    void overdueKeyCarriesTheReachedDateSoItRemindsOncePerCycle() {
        // When — 같은 사이클을 이틀 연속 점검한다
        subscriber.onReminderDue(overdue(DUE, RUN));
        subscriber.onReminderDue(overdue(DUE, RUN.plusDays(1)));

        // Then — 키가 같다. 안 풀린 같은 사건을 매일 알리면 소음이다
        assertThat(keysOf(4))
                .containsExactly("overdue:7:2026-09-01", "overdue:7:2026-09-01",
                        "overdue:7:2026-09-01", "overdue:7:2026-09-01");
    }

    @Test
    @DisplayName("F3-2 — 재개 후 다시 100%가 되면 도달일이 달라져 새 사이클이 된다")
    void overdueStartsANewCycleWhenTheReachedDateChanges() {
        // When
        subscriber.onReminderDue(overdue(DUE, RUN));
        subscriber.onReminderDue(overdue(DUE.plusDays(30), RUN.plusDays(30)));

        // Then
        assertThat(keysOf(4)).contains("overdue:7:2026-09-01", "overdue:7:2026-10-01");
    }

    @Test
    @DisplayName("F3-1 — 완료 지연은 PM·PL 모두에게 한 건씩 간다")
    void overdueReachesEveryLead() {
        // When
        subscriber.onReminderDue(overdue(DUE, RUN));

        // Then
        ArgumentCaptor<NotifyCommand> commands = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notificationService, org.mockito.Mockito.times(2)).notify(commands.capture());
        assertThat(commands.getAllValues())
                .extracting(NotifyCommand::recipientId)
                .containsExactly(PM_ID, PL_ID);
        assertThat(commands.getAllValues())
                .allSatisfy(command -> assertThat(command.type())
                        .isEqualTo(NotificationType.COMPLETION_OVERDUE));
    }

    @Test
    @DisplayName("F3-3 — 재개는 알림을 만들지 않고 미읽음 완료 지연을 회수한다")
    void reopenWithdrawsInsteadOfNotifying() {
        // When
        subscriber.onLifecycleChanged(new ProjectLifecycleChanged(
                ProjectLifecycleChanged.Kind.REOPENED, PROJECT_ID, "MES 구축",
                List.of(PM_ID, PL_ID)));

        // Then
        verify(notificationService).withdrawUnread(
                "Project", PROJECT_ID, NotificationType.COMPLETION_OVERDUE);
        verify(notificationService, never()).notify(any());
    }

    @Test
    @DisplayName("§8 — 완료는 배정 인원 전원에게 안내를 보내고 회수하지 않는다")
    void completionNotifiesEveryMember() {
        // When
        subscriber.onLifecycleChanged(new ProjectLifecycleChanged(
                ProjectLifecycleChanged.Kind.COMPLETED, PROJECT_ID, "MES 구축",
                List.of(PM_ID, PL_ID)));

        // Then
        assertThat(keysOf(2)).containsExactly("completed:7:13", "completed:7:21");
        verify(notificationService, never()).withdrawUnread(any(), org.mockito.ArgumentMatchers
                .anyLong(), any());
    }

    private List<String> keysOf(int times) {
        ArgumentCaptor<NotifyCommand> commands = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notificationService, org.mockito.Mockito.times(times)).notify(commands.capture());

        return commands.getAllValues().stream().map(NotifyCommand::dedupeKey).toList();
    }

    private NotifyCommand capture() {
        ArgumentCaptor<NotifyCommand> command = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notificationService).notify(command.capture());

        return command.getValue();
    }

    private static ProjectReminderDue deadline(LocalDate runDate) {
        return new ProjectReminderDue(ProjectReminderDue.Kind.DEADLINE_NEAR, PROJECT_ID,
                "MES 구축", DUE, runDate, List.of(PM_ID));
    }

    private static ProjectReminderDue overdue(LocalDate reachedOn, LocalDate runDate) {
        return new ProjectReminderDue(ProjectReminderDue.Kind.COMPLETION_OVERDUE, PROJECT_ID,
                "MES 구축", reachedOn, runDate, List.of(PM_ID, PL_ID));
    }
}
