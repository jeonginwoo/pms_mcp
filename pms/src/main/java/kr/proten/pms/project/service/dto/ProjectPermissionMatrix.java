package kr.proten.pms.project.service.dto;

import java.util.List;

/**
 * 역할×기능 매트릭스 (AC A8-1) — 기본값 + override 병합 결과와 셀별 고정 여부.
 *
 * <p>{@code version}은 {@code Project.version}이다(A8-7 — 공용 락). 조회로 받은 값을
 * 저장에 그대로 실어 보내는 왕복이 §7 규칙이다.
 *
 * <p>화면이 잠금 표시를 그릴 수 있어야 하므로 <b>고정 칸도 빠짐없이 담는다</b> —
 * 조정 가능한 8칸만 주면 화면은 나머지를 스스로 알아야 하고, 그것이 §4-2 표의
 * 사본을 화면에 만드는 길이다.
 */
public record ProjectPermissionMatrix(long projectId, List<Cell> cells, long version) {

    /**
     * 한 칸.
     *
     * @param allowed 병합 결과 — 지금 이 프로젝트에서 그 역할이 그 기능을 할 수 있는가
     * @param editable PM이 이 칸을 조정할 수 있는가 (§4-2 고정 칸이면 false)
     * @param overridden 기본값과 달라서 저장된 칸인가 — 화면의 "기본값 복원" 표시용
     */
    public record Cell(
            String role, String action, boolean allowed, boolean editable, boolean overridden) {
    }
}
