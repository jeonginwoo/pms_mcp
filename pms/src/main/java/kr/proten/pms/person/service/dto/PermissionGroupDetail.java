package kr.proten.pms.person.service.dto;

/**
 * 권한 그룹 상세 (US-E5 — 규칙 원본은 상위 PRD §4-3).
 *
 * `visibilityScope`가 enum이 아니라 문자열인 이유: 값 자체는 person의 영속 모델
 * (`service/entity`)에 있고 그 패키지는 모듈 밖으로 열지 않는다. `GET /api/me`가
 * 이미 같은 방식으로 scope 이름을 내보내고 있다.
 *
 * @param visibilityScope COMPANY | DIVISION | TEAM(하위 포함) | SELF
 * @param systemFixed     관리자 그룹 — 수정·삭제 시 `422 IMMUTABLE_GROUP`(E5-3, 자기 잠금 방지)
 */
public record PermissionGroupDetail(
        Long id,
        String name,
        String visibilityScope,
        boolean createProject,
        boolean manageContracts,
        boolean manageAllProjects,
        boolean manageOrg,
        boolean systemFixed,
        long version) {
}
