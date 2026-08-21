package kr.proten.pms.project.service.entity;

/**
 * phase — status에서 파생하는 화면 그룹 (PRD-pms §5, v2.4).
 * 저장 컬럼이 아니라 서버 단일 정의의 파생값이다 — 원본을 이중화하지 않는다.
 *
 * 유지보수중은 어느 phase에도 들지 않는다: 유지보수 탭의 원천은 프로젝트가 아니라
 * 계약(MaintenanceContract)이므로 프로젝트 목록의 그룹 축이 아니다(§5).
 */
public enum ProjectPhase {
    // 계약대기 · 수주확정
    SALES("영업"),
    // 진행중 · 완료
    SOLUTION("솔루션");

    private final String label;

    ProjectPhase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
