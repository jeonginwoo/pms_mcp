package kr.proten.pms.project;

import java.util.List;

/**
 * 프로젝트가 완료됐거나 재개됐다 — project가 발행하고 notification이 구독한다
 * (§8 {@code ProjectCompleted} · {@code ProjectReopened}).
 *
 * <p><b>§8이 2026-08-24에 명세했는데 발행 지점이 0곳이었다</b>(2026-08-25 실측).
 * {@code NotificationType.PROJECT_COMPLETED}는 존재했고 회수 메서드
 * ({@code NotificationService.withdrawUnread})도 구현·테스트돼 있었지만 <b>실사용
 * 호출자가 없어</b> 재개해도 완료 지연 알림이 회수되지 않았다 — 능력만 있고 배선이
 * 없던 자리다. 이 타입이 그 둘을 잇는다.
 *
 * <p>{@code AssignmentChanged}·{@code ProjectReminderDue}와 같은 이유로 한 타입에
 * {@link Kind} 둘을 담는다: 발행 지점이 같고({@code transition} 한 곳) 구독자가 갈리는
 * 지점도 {@code kind} 하나다.
 *
 * @param assigneeIds 배정된 인원 — 완료 안내의 수신자 재료다(§8 "완료/이관 안내").
 *                    재개에는 쓰이지 않는다: 회수는 대상 프로젝트로 걷는다(F3-3)
 */
public record ProjectLifecycleChanged(
        Kind kind,
        long projectId,
        String projectName,
        List<Long> assigneeIds) {

    /** 무엇이 일어났나 — §8의 두 이벤트에 대응한다. */
    public enum Kind {
        /** `ProjectCompleted` — 완료 처리 (US-A7). 배정 인원에게 안내 */
        COMPLETED,
        /**
         * `ProjectReopened` — 재개 (US-A7-3).
         * 알림을 만들지 않고 <b>미읽음 완료 지연 알림을 회수</b>한다(F3-3).
         */
        REOPENED
    }
}
