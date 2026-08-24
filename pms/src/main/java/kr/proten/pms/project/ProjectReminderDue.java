package kr.proten.pms.project;

import java.time.LocalDate;
import java.util.List;

/**
 * 일일 점검이 찾아낸 리마인드 대상 — project가 발행하고 notification이 구독한다
 * (AC F2-1 · F3-1).
 *
 * <p><b>§8 표에 없던 이벤트다</b>(2026-08-25 신설 — 공용 결정 기록). §8은 F2·F3의
 * 알림 <i>유형</i>({@code DEADLINE_NEAR}·{@code COMPLETION_OVERDUE})만 정하고 그것이
 * 어떻게 만들어지는지는 비워 뒀는데, 이 프로젝트의 규칙상 <b>notification 밖에서
 * {@code notify}를 부르는 모듈은 없다</b>. 그래서 스케줄러도 발행을 하고 구독자가
 * 적재한다 — 스케줄러가 직접 알림을 만들면 그 규칙만 예외가 된다.
 *
 * <p><b>스케줄러가 project에 있는 이유</b>: "어느 프로젝트가 D-7인가"·"어느 프로젝트가
 * 100%인 채 멎었는가"는 <b>프로젝트 판단</b>이고 그 데이터는 project가 갖는다.
 * notification에 두면 그 판단이 notification으로 넘어가고, project는 화자 없는
 * 조회를 두 개 더 열어야 한다({@code ProjectLookupService}는 가시성 판정이 있어
 * 스케줄러가 쓸 수 없다).
 *
 * <p><b>{@link Kind} 하나에 둘을 담는다</b>: {@code AssignmentChanged}와 같은 판단이다 —
 * 두 사건이 하는 말이 "이 프로젝트를 들여다볼 때가 됐다"로 같고, 구독자가 하는 일도
 * 수신자에게 한 건 적재로 같다. 갈리는 것은 문구와 유형뿐이라 {@code kind}로 묻는다.
 *
 * <p><b>{@code recipientIds}는 수신자 명단이 아니라 프로젝트에 대한 사실이다</b> —
 * 마감 임박은 PM(F2-1), 완료 지연은 PM·PL(F3-1)이고 그 역할 판정은 project의 것이다.
 * 누구에게 보낼지의 최종 결정(설정 꺼짐 필터 등)은 여전히 구독자가 한다.
 *
 * @param dueDate 마감 임박이면 종료일, 완료 지연이면 100% 도달일 — 문구의 재료다
 * @param runDate 점검이 돈 날 — <b>마감 임박의 멱등 단위</b>다(F2-2 "같은 날 재실행").
 *                종료일을 키에 쓰면 종료일당 평생 1건이 되어 "일일 점검"이 성립하지
 *                않는다(2026-08-25 리뷰 실측 — 주석이 코드보다 크게 말하고 있었다).
 *                완료 지연은 이 값을 키에 쓰지 않는다: 같은 사건이 안 풀린 것이라
 *                한 사이클에 한 번이면 된다(F3-2)
 */
public record ProjectReminderDue(
        Kind kind,
        long projectId,
        String projectName,
        LocalDate dueDate,
        LocalDate runDate,
        List<Long> recipientIds) {

    /** 무엇을 리마인드하나 — AC F2-1 · F3-1. */
    public enum Kind {
        /** 종료일 D-7 이내인 진행중 프로젝트 → PM (F2-1) */
        DEADLINE_NEAR,
        /** 100%인 채 7일 경과한 진행중 프로젝트 → PM·PL (F3-1) */
        COMPLETION_OVERDUE
    }
}
