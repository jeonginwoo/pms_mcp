package kr.proten.pms.project.service.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * §4-2 기본 매트릭스와 <b>고정 칸 규칙</b>의 단일 정의 (US-A8).
 *
 * <p>표가 여기 하나인 것이 규칙이다. 판정(`ProjectActionPermission`)·조회(A8-1의
 * editable)·저장 검증(A8-4의 422)이 <b>같은 표를 세 방향으로</b> 읽는다 — 방향마다
 * 표를 손으로 쓰면 §4-2의 한 칸이 바뀔 때 한쪽만 고쳐도 아무것도 실패하지 않는다
 * ({@code ProjectPhase}에서 같은 이유로 표를 한 벌로 만든 선례 — 2026-08-25).
 *
 * <p><b>고정 칸</b>(§4-2 "고정(토글 불가)"): ①PM 열 전체 — 매트릭스를 고치는 역할이
 * 스스로 잠기면 복구 불가하고 §4-1 ADMIN 치환의 수습 보장도 이 열이 전제다
 * ②{@code HANDOVER} 행 — 비가역 행위의 안전장치. 조회·삭제 행도 고정이지만 그 둘은
 * 애초에 {@link ProjectAction}이 아니다(조회는 가시성의 몫이고, 삭제는 판정 축이
 * 둘이라 {@code requireDelete}가 따로 든다).
 */
public final class ProjectPermissionRules {

    /** 상위 PRD §4-2 기본값 — 완료·재개는 진척률과 같은 실무 경로라 배정 전원이다 */
    private static final Map<ProjectAction, Set<ProjectRole>> DEFAULTS = defaults();

    private ProjectPermissionRules() {
    }

    /** 그 기능의 기본 허용 역할들 (§4-2 표 한 행) */
    public static Set<ProjectRole> defaultRoles(ProjectAction action) {
        return DEFAULTS.get(action);
    }

    /** 기본값에서 그 칸이 허용인가 */
    public static boolean allowedByDefault(ProjectRole role, ProjectAction action) {
        return DEFAULTS.get(action).contains(role);
    }

    /**
     * 그 칸을 PM이 조정할 수 있는가 (A8-1의 {@code editable} · A8-4의 422 판정).
     * 조정 가능한 칸은 {PL, 참여자} × {정보수정, 배정, 진척률, 완료·재개} <b>8개뿐</b>이다.
     */
    public static boolean editable(ProjectRole role, ProjectAction action) {
        return editableRoles(action).contains(role);
    }

    /**
     * §4-2 "조정 가능" 목록 — <b>허용 목록</b>이지 금지 목록이 아니다.
     *
     * <p>{@code role != PM && action != HANDOVER}로 적으면 짧지만 <b>새 기능이 조용히
     * 조정 가능해진다</b>: {@link ProjectAction}에 값이 하나 늘면 그것이 §4-2의 고정 행일
     * 때조차 PM이 토글할 수 있게 되고, 컴파일도 테스트도 아무것도 실패하지 않는다.
     * {@code default} 없는 switch가 그때 <b>일부러</b> 컴파일을 깨서 누군가
     * "이 기능은 조정 가능한가"를 결정하게 만든다({@code ProjectPhase.of}·
     * {@code ToolError.from} 선례).
     */
    private static Set<ProjectRole> editableRoles(ProjectAction action) {
        return switch (action) {
            case EDIT_INFO, ASSIGN, PROGRESS, COMPLETE_REOPEN ->
                    EnumSet.of(ProjectRole.PL, ProjectRole.PARTICIPANT);
            // 이관은 §4-2 고정 행이다 — 비가역 행위의 안전장치
            case HANDOVER -> EnumSet.noneOf(ProjectRole.class);
        };
    }

    private static Map<ProjectAction, Set<ProjectRole>> defaults() {
        Map<ProjectAction, Set<ProjectRole>> table = new EnumMap<>(ProjectAction.class);
        table.put(ProjectAction.EDIT_INFO, EnumSet.of(ProjectRole.PM, ProjectRole.PL));
        table.put(ProjectAction.ASSIGN, EnumSet.of(ProjectRole.PM));
        table.put(ProjectAction.PROGRESS, EnumSet.of(ProjectRole.PM, ProjectRole.PL, ProjectRole.PARTICIPANT));
        table.put(ProjectAction.COMPLETE_REOPEN, EnumSet.of(ProjectRole.PM, ProjectRole.PL, ProjectRole.PARTICIPANT));
        // 이관은 PM 하나다(D1) — 완료·재개와 달리 실무 경로가 아니라
        // 프로젝트를 유지보수로 넘기는 마지막 결정이다
        table.put(ProjectAction.HANDOVER, EnumSet.of(ProjectRole.PM));

        // 표에 빠진 기능이 있으면 판정이 NPE로 터진다 — 열거가 늘면 여기서 막는다
        for (ProjectAction action : ProjectAction.values()) {
            if (!table.containsKey(action)) {
                throw new IllegalStateException("§4-2 기본값이 없는 기능: " + action);
            }
        }
        table.replaceAll((action, roles) -> Collections.unmodifiableSet(roles));

        return Collections.unmodifiableMap(table);
    }
}
