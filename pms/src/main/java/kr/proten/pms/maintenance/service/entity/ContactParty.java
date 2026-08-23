package kr.proten.pms.maintenance.service.entity;

/** 연락처 구분 (PRD-pms §4) — 계약사(우리에게 발주한 쪽)와 고객사(실제 사용처). */
public enum ContactParty {
    CONTRACTOR("계약사"),
    CLIENT("고객사");

    private final String label;

    ContactParty(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
