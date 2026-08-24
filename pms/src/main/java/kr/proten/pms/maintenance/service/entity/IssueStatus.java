package kr.proten.pms.maintenance.service.entity;

/**
 * 이슈 상태 (PRD-pms D3-2) — 접수 → 처리중 → 고객확인대기(선택) → 완료.
 * 역방향은 재개(완료 → 처리중)만 허용한다.
 *
 * <p>전이 그래프를 여기에 둔다 — {@code ProjectStatus.next()}·{@code advancesTo}의
 * 선례다(2026-08-24 D3 구현 시 확정. 구 주석은 "전이 규칙은 쓰기 구현의 몫"이라고
 * 적었지만, 그러면 상태 열거를 읽는 사람이 무엇이 허용되는지 알 수 없고 규칙이
 * 호출자마다 갈린다). 열거가 자기 그래프를 알고, 그것을 <b>거절</b>하는 것은
 * 엔티티의 몫이다({@link MaintenanceIssue#changeStatus}).
 */
public enum IssueStatus {
    RECEIVED("접수"),
    IN_PROGRESS("처리중"),
    AWAITING_CLIENT("고객확인대기"),
    DONE("완료");

    private final String label;

    IssueStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 열린 이슈인가 — "내 담당 열린 이슈"(D3-4)의 판정. */
    public boolean isOpen() {
        return this != DONE;
    }

    /**
     * 이 상태에서 {@code target}으로 갈 수 있는가 (AC D3-2).
     *
     * <p>{@code 고객확인대기}가 <b>선택</b>이라 처리중에서 완료로 바로 갈 수 있다.
     * 반대로 접수에서 완료로 건너뛰는 것은 AC의 흐름에 없어 허용하지 않는다 —
     * 처리하지 않은 이슈가 완료로 끝나면 "누가 무엇을 했나"가 이력에서 사라진다.
     *
     * <p>역방향 한 칸만 열려 있다: 완료 → 처리중(재개). 고객확인대기에서 처리중으로
     * 되돌리는 것도 역방향이라 막는다 — AC가 예외로 적은 것은 재개 하나다.
     *
     * <p>같은 상태로의 전이는 false다. 바뀌지 않는 것은 전이가 아니고, 호출자가
     * 상태를 함께 보내면서 담당자만 바꾸는 경우는 {@code null}로 표현한다.
     */
    public boolean canTransitionTo(IssueStatus target) {
        return switch (this) {
            case RECEIVED -> target == IN_PROGRESS;
            case IN_PROGRESS -> target == AWAITING_CLIENT || target == DONE;
            case AWAITING_CLIENT -> target == DONE;
            case DONE -> target == IN_PROGRESS;
        };
    }
}
