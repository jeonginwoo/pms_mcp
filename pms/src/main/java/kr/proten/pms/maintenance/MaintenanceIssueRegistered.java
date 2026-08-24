package kr.proten.pms.maintenance;

/**
 * 유지보수 이슈가 등록됐다 — maintenance가 발행하고 notification이 구독한다 (§8).
 *
 * <p>모듈 루트에 있는 이유는 구독 방향이다: 간선은 언제나 <b>구독자 → 발행자</b>이고
 * (2026-08-24 확정 — PRD-pms §3·§8), 그래서 notification이 이 타입을 import한다.
 * 거꾸로 maintenance가 {@code NotificationService.notify}를 부르면 반대 간선이 함께
 * 생겨 순환이 된다.
 *
 * <p><b>담당자가 없어도 발행한다</b>: 이 이벤트가 말하는 사실은 "이슈가 등록됐다"이고
 * 그것은 담당 지정 여부와 무관하게 참이다(사이트에 담당 엔지니어가 없으면
 * {@code assigneeId}가 비어 온다). 알릴 사람이 없다는 판단은 구독자가 한다 —
 * {@code AssignmentChanged}에서 notification만 {@code ASSIGNED}로 거르는 것과 같은
 * 배치다. 발행 측이 "알림이 갈지"를 알면 발행자가 구독자를 아는 것이 된다.
 *
 * <p>문구를 만들 재료를 실어 보낸다({@code title}·{@code siteName}) — 구독자가 되물으면
 * {@code notification → maintenance} 간선이 새로 생기고, 그것은 이 이벤트가 피하려던
 * 방향이다.
 *
 * @param assigneeId 담당자 — 비어 있으면 미배정이다
 * @param siteName   사이트명 — 이슈가 어느 고객사 것인지가 알림의 절반이다
 */
public record MaintenanceIssueRegistered(
        long issueId,
        String title,
        Long assigneeId,
        String siteName) {
}
