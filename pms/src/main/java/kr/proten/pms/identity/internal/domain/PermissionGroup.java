package kr.proten.pms.identity.internal.domain;

/**
 * 권한 그룹 — 구 orgRole 4단의 일반화 (2026-08-09 ⑦, 규칙 원본은 상위 PRD §4-3).
 * 가시성 scope 4단 + 프로젝트 밖 기능 플래그 4종. 판정·가시성·404 은닉이 전부
 * 그룹 정의를 따른다. systemFixed 그룹(관리자)은 수정·삭제 불가 — 자기 잠금 방지.
 */
public record PermissionGroup(
        Long id,
        String name,
        VisibilityScope visibilityScope,
        boolean createProject,
        boolean manageContracts,
        boolean manageAllProjects,
        boolean manageOrg,
        boolean systemFixed,
        long version) {
}
