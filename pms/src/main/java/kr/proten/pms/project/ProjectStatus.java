package kr.proten.pms.project;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

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

    /**
     * 사람이 <b>아직 물려 있는</b> 상태 — 완료·유지보수중을 뺀 셋 (2026-08-26 신설).
     *
     * <p>이 집합이 생긴 이유는 실측이다: 완료 프로젝트의 배정이 {@code ACTIVE}로 살아
     * 있다. 시드된 DB에서 ACTIVE 배정 462건 중 <b>384건(83%)이 완료 프로젝트</b>의
     * 것이고, 한 사람은 128건 중 진행 중이 5건뿐이었다. 배정 종료(AC B2-1)는
     * {@code DELETE /assignments/{id}}라는 <b>명시적 수동 행위</b>이고 완료 시 자동으로
     * 종료하는 규칙은 어떤 AC에도 없다 — {@code AssignmentCountAdapter}의 옛 주석이
     * "완료 시 이미 종료된다(B2-1)"고 적었던 것은 <b>사실이 아니었다</b>.
     *
     * <p>그래서 "물려 있는가"는 <b>배정 상태와 프로젝트 상태를 함께</b> 봐야 답이 된다
     * (2026-08-26 사용자 결정 — 데이터를 고치는 대신 판정으로 거른다. 자동 종료를
     * 만들면 B2-1과 종료 경로가 둘로 갈리고 재개(A7-3)에 되살리기 규칙이 붙는다).
     *
     * <p>여기 두는 것은 <b>묻는 쪽이 둘</b>이기 때문이다 — 이동 경고(E1-2)와 퇴사
     * 처리(§12 ③). 각자 상태 목록을 적으면 한쪽만 고쳐질 때 두 화면이 다른 수를 낸다.
     */
    private static final Set<ProjectStatus> LIVE =
            EnumSet.of(CONTRACT_PENDING, ORDER_CONFIRMED, IN_PROGRESS);

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

    /**
     * 아직 사람이 물려 있는 상태인가 — 완료·유지보수중은 false다 ({@link #LIVE}).
     *
     * <p>질의에 넘길 목록이 필요하면 {@link #live()}를 쓴다. JPQL은 이 메서드를 부를
     * 수 없어 집합을 파라미터로 받아야 하기 때문이고, 그래도 <b>정의는 여전히 한 곳</b>이다.
     */
    public boolean isLive() {
        return LIVE.contains(this);
    }

    /** 질의 파라미터용 — 위 정의의 사본을 만들지 않는다. */
    public static Set<ProjectStatus> live() {
        return LIVE;
    }
}
