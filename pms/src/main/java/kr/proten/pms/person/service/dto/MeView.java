package kr.proten.pms.person.service.dto;

/**
 * 내 계정 (PRD-pms §7 `GET /api/me`) — 화면이 권한에 따라 UI를 정리하는 근거다.
 *
 * 판정은 언제나 서버가 한다(상위 PRD §4-1) — 이 값은 "누를 수 없는 버튼을 보여
 * 주지 않기" 위한 표시용이다. 그래서 플래그만 담고 판정 결과(예: 이 프로젝트를
 * 삭제할 수 있는가)는 담지 않는다: 그것은 프로젝트마다 다른 질문이다.
 *
 * @param visibilityScope 표기용 문자열 — COMPANY·DIVISION·TEAM·SELF (상위 PRD §4-4)
 */
public record MeView(
        Long id,
        String name,
        String orgUnit,
        String grade,
        String group,
        String visibilityScope,
        boolean createProject,
        boolean manageContracts,
        boolean manageAllProjects,
        boolean manageOrg) {
}
