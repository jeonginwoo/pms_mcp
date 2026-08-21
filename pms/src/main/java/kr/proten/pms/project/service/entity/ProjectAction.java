package kr.proten.pms.project.service.entity;

/**
 * 프로젝트 안에서 역할별로 갈리는 기능 (PRD-pms §4 `ProjectPermissionOverride.action`).
 *
 * 네 가지뿐인 것이 규칙이다: 조회·삭제·이관은 역할별로 조정할 수 없는 고정 셀이고
 * (상위 PRD §4-2), 완료 처리와 재개는 한 토글로 묶인다(A8-4의 유효 action = 이 4종).
 * 기본 판정 표는 {@code ProjectActionPermission}이 갖는다.
 */
public enum ProjectAction {
    EDIT_INFO,
    ASSIGN,
    PROGRESS,
    COMPLETE_REOPEN
}
