package kr.proten.pms.maintenance.service.entity;

/**
 * 이슈 상태 (PRD-pms D3-2) — 접수 → 처리중 → 고객확인대기(선택) → 완료.
 * 역방향은 재개(완료 → 처리중)만 허용한다. 전이 규칙 자체는 쓰기(US-D3) 구현의 몫이고
 * 여기서는 값만 정의한다.
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
}
