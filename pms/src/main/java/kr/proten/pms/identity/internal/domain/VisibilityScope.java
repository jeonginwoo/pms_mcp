package kr.proten.pms.identity.internal.domain;

/**
 * 권한 그룹의 조직 가시성 4단 (상위 PRD §4-3).
 * TEAM은 소속 노드의 하위 조직(subtree)을 포함한다 — 판정 구현은 PMS-M1b.
 */
public enum VisibilityScope {
    // 전사
    COMPANY,
    // 소속 부문
    DIVISION,
    // 소속 팀 + 하위 조직(subtree)
    TEAM,
    // 본인 참여분만
    SELF
}
