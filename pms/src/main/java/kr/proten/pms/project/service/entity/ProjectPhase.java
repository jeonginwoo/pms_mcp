package kr.proten.pms.project.service.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import kr.proten.pms.project.ProjectStatus;

/**
 * phase — status에서 파생하는 화면 그룹 (PRD-pms §5, v2.4).
 * 저장 컬럼이 아니라 서버 단일 정의의 파생값이다 — 원본을 이중화하지 않는다.
 *
 * 유지보수중은 어느 phase에도 들지 않는다: 유지보수 탭의 원천은 프로젝트가 아니라
 * 계약(MaintenanceContract)이므로 프로젝트 목록의 그룹 축이 아니다(§5).
 *
 * <p><b>표는 {@link #of(ProjectStatus)}의 switch 하나이고 나머지는 그것에서 파생한다.</b>
 * 읽는 방향이 둘이기 때문이다 — 단건 응답은 status → phase({@code Project.getPhase()}),
 * 목록 필터({@code ?phase=})는 phase → status 집합이다. 방향마다 표를 손으로 쓰면 §5가
 * 금지하는 이중화가 저장 컬럼이 아니라 코드에서 재현되고, 상태가 하나 늘 때 한쪽만
 * 고쳐도 아무것도 실패하지 않는다.
 *
 * <p>표를 열거 상수의 인자로 싣지 않고 <b>switch로 두는 것이 규칙</b>이다: 그래야
 * {@code default} 없는 switch가 {@code ProjectStatus}가 늘 때 <b>일부러 컴파일을 깨서</b>
 * 누군가 "이 상태는 어느 phase인가"를 결정하게 만든다({@code ToolError.from} 선례).
 * 상수에 집합을 싣는 방식은 그 강제를 잃는다 — 새 상태가 조용히 <b>양쪽 탭에서 빠져</b>
 * 유지보수중과 같은 취급을 받고, 컴파일도 테스트도 아무것도 실패하지 않는다.
 */
public enum ProjectPhase {
    SALES("영업"),
    SOLUTION("솔루션");

    /*
     * phase → status 집합. 상수 초기화가 끝난 뒤 돌아야 하므로(of가 상수를 돌려준다)
     * 인스턴스 필드가 아니라 정적 표에 담는다.
     */
    private static final Map<ProjectPhase, Set<ProjectStatus>> STATUSES = buildStatuses();

    private final String label;

    ProjectPhase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 이 그룹에 드는 상태들 — 목록 필터가 질의 조건으로 내려보낸다 (AC A3-1). */
    public Set<ProjectStatus> statuses() {
        return STATUSES.get(this);
    }

    /**
     * 그 상태가 드는 그룹 — 어디에도 들지 않으면 {@code null}이다(유지보수중).
     * null을 쓰는 이유는 §7 단건 응답의 파생 필드가 "없음"을 그렇게 표현하기 때문이다.
     *
     * <p>이 switch에 {@code default}가 없는 것이 이 열거의 안전장치다 — 위 주석 참조.
     */
    public static ProjectPhase of(ProjectStatus status) {
        return switch (status) {
            case CONTRACT_PENDING, ORDER_CONFIRMED -> SALES;
            case IN_PROGRESS, COMPLETED -> SOLUTION;
            case UNDER_MAINTENANCE -> null;
        };
    }

    private static Map<ProjectPhase, Set<ProjectStatus>> buildStatuses() {
        Map<ProjectPhase, Set<ProjectStatus>> grouped = new EnumMap<>(ProjectPhase.class);
        for (ProjectPhase phase : values()) {
            grouped.put(phase, EnumSet.noneOf(ProjectStatus.class));
        }
        for (ProjectStatus status : ProjectStatus.values()) {
            ProjectPhase phase = of(status);
            if (phase != null) {
                grouped.get(phase).add(status);
            }
        }
        // 불변으로 감싼다 — 이 집합은 저장소 질의 인자로 모듈 밖까지 나간다
        grouped.replaceAll((phase, statuses) -> Collections.unmodifiableSet(statuses));

        return Collections.unmodifiableMap(grouped);
    }
}
