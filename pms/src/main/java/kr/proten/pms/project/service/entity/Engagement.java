package kr.proten.pms.project.service.entity;

/**
 * 수행 형태 (PRD-pms §4) — 원격·상주·부분상주 3종.
 * 구 OFFSITE는 폐지되었고 기존 데이터는 적재 시 REMOTE로 흡수한다(부록 B).
 */
public enum Engagement {
    REMOTE("원격"),
    ONSITE("상주"),
    PARTIAL_ONSITE("부분상주");

    private final String label;

    Engagement(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
