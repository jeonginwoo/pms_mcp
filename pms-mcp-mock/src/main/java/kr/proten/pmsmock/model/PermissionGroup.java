package kr.proten.pmsmock.model;

/**
 * 권한 그룹 (상위 PRD §4-3 — 2026-08-09 일반화).
 * 목업은 실험에 필요한 것만 담는다: 가시성 scope + "전 프로젝트 관리" 플래그(PM 간주 치환).
 * 나머지 기능 플래그 3종(프로젝트 생성·계약 관리·사용자/조직/권한 관리)은 노출 도구가 없어 생략.
 */
public record PermissionGroup(String name, VisibilityScope scope, boolean manageAllProjects) {
}
