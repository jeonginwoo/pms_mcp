package kr.proten.pms.maintenance.service.entity;

/**
 * 계약 상태 (PRD-pms §4) — MCP {@code search_maintenance}의 status 필터와 <b>같은
 * 집합</b>이다(2026-08-11 양측 합의). 값을 늘리면 도구 description·목업 검증 문구·
 * eval이 한 세트로 따라오므로(상위 PRD §6) 여기서 임의로 늘리지 않는다.
 *
 * <p>시드의 '자동연장'·'갱신' 2건은 적재 시 {@link #ACTIVE}로 흡수하고 원문은
 * 계약 note에 남긴다(2026-08-23 결정) — 둘 다 실제로 유지 중인 계약이다.
 */
public enum ContractStatus {
    PLANNED("예정"),
    NEW("신규"),
    ACTIVE("유지"),
    ENDED("종료");

    private final String label;

    ContractStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
