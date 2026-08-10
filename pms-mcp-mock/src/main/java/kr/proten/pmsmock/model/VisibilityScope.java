package kr.proten.pmsmock.model;

/** 권한 그룹의 가시성 scope 4단 (상위 PRD §4 — 2026-08-09 일반화) */
public enum VisibilityScope {
    COMPANY,   // 전사
    DIVISION,  // 자기 부문
    TEAM,      // 자기 팀 (실전은 하위 조직 subtree 포함 — 목업은 평면 팀)
    SELF       // 본인 참여
}
