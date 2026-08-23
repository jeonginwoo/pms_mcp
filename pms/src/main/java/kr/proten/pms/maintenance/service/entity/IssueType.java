package kr.proten.pms.maintenance.service.entity;

/**
 * 이슈 유형 (PRD-pms §4) — MCP {@code list_maintenance_logs}의 type 필터와 같은 집합
 * (도구 description "장애/문의/요청").
 */
public enum IssueType {
    INCIDENT("장애"),
    INQUIRY("문의"),
    REQUEST("요청");

    private final String label;

    IssueType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
