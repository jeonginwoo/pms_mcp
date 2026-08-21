package kr.proten.pms.project.service.entity;

import java.util.Optional;

/**
 * 프로젝트 상태 (PRD-pms §5): 계약대기 → 수주확정 → 진행중 → 완료 → 유지보수중.
 * 역방향 전이는 금지이며 유일한 예외가 재개(완료→진행중)다 — 재개·완료·이관은
 * 전용 경로만의 몫이라 이 열거가 아는 전이는 순방향 두 칸뿐이다.
 */
public enum ProjectStatus {
    CONTRACT_PENDING("계약대기"),
    ORDER_CONFIRMED("수주확정"),
    IN_PROGRESS("진행중"),
    COMPLETED("완료"),
    UNDER_MAINTENANCE("유지보수중");

    // 화면·챗에 그대로 쓰는 한국어 표기 (상위 PRD 용어와 일치)
    private final String label;

    ProjectStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 정보 수정 경로(`PUT /projects/{id}`)로 갈 수 있는 다음 상태 (AC A5-1).
     *
     * 진행중 이후가 비어 있는 것이 규칙이다: 완료는 명시적 완료 처리(US-A7),
     * 유지보수중은 이관(US-D1)만이 만든다 — 정보 수정 폼이 상태를 자유 편집하면
     * 그 권한 경계가 무너진다(2026-08-02 서버 강제 결정).
     */
    public Optional<ProjectStatus> next() {
        return switch (this) {
            case CONTRACT_PENDING -> Optional.of(ORDER_CONFIRMED);
            case ORDER_CONFIRMED -> Optional.of(IN_PROGRESS);
            case IN_PROGRESS, COMPLETED, UNDER_MAINTENANCE -> Optional.empty();
        };
    }

    /** 정보 수정 경로로 target까지 갈 수 있는가 — 건너뛰기·역방향은 false다. */
    public boolean advancesTo(ProjectStatus target) {
        return next().filter(target::equals).isPresent();
    }
}
