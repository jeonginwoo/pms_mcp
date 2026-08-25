package kr.proten.pms.project.service.dto;

import java.util.List;
import kr.proten.pms.project.service.entity.ProjectAction;
import kr.proten.pms.project.service.entity.ProjectRole;

/**
 * 권한 매트릭스 저장 (AC A8-2) — <b>전체 교체</b>다.
 *
 * <p>{@code overrides}가 비면 전체 기본값 복원이다(A8-2 — "별도 API 없음"). 그래서
 * 부분 갱신(PATCH)이 아니라 지금 상태 전부를 보낸다: 부분 갱신이면 "이 칸을
 * 기본값으로 되돌린다"를 표현할 방법이 삭제 API 하나 더로 생긴다.
 *
 * <p>{@code role}·{@code action}을 열거로 받는 것이 규칙이다 — 모르는 이름은 스프링의
 * 열거 변환이 §7 봉투 400으로 답한다(`?phase=` 선례, 2026-08-25). 고정 칸을 담은
 * 요청은 그와 달리 <b>422 IMMUTABLE_PERMISSION</b>이다: 그 칸은 실재하고 값도 있지만
 * 조정만 막혀 있다는 뜻이라, "그런 칸은 없다"와 같은 답을 주면 안 된다.
 */
public record UpdateProjectPermissionsCommand(List<Override> overrides, long version) {

    public record Override(ProjectRole role, ProjectAction action, boolean allowed) {
    }
}
