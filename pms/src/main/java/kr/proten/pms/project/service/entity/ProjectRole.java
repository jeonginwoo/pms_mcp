package kr.proten.pms.project.service.entity;

/**
 * 프로젝트 역할 3단 (상위 PRD §4-2) — 프로젝트마다 개별 판정하며 전역 역할이 아니다.
 * 정본은 배정 레코드(ProjectAssignment.role)다. 역할 신설은 Out of Scope.
 * PARTICIPANT라는 이름을 쓰는 이유: 조직 권한 쪽 MEMBER와 이름이 겹치면 두 축이
 * 섞여 읽힌다(PRD-pms §4).
 */
public enum ProjectRole {
    PM,
    PL,
    PARTICIPANT
}
