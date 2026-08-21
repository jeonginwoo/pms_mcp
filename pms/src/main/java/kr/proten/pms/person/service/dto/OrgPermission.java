package kr.proten.pms.person.service.dto;

/**
 * 권한 그룹의 기능 플래그 — 프로젝트 밖 행위만 판정한다 (상위 PRD §4-3).
 * 프로젝트 안 권한은 project 모듈의 역할 판정 소관이며, 유일한 교차점은
 * MANAGE_ALL_PROJECTS(모든 프로젝트에서 PM 간주 — §4-1 치환)다.
 */
public enum OrgPermission {
    // 프로젝트 생성
    CREATE_PROJECT,
    // 유지보수 계약·사이트 등록/수정
    MANAGE_CONTRACTS,
    // 모든 프로젝트에서 PM 간주 (§4-1 치환)
    MANAGE_ALL_PROJECTS,
    // 사용자·조직·직급·권한 그룹 관리
    MANAGE_ORG
}
