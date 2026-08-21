package kr.proten.pms.person.service.entity;

/**
 * 권한 그룹의 조직 가시성 4단 (상위 PRD §4-3).
 * TEAM은 소속 노드의 하위 조직(subtree)을 포함하고, DIVISION은 소속 경로상
 * 최상위 부문의 subtree를 뜻한다. scope별 해석은 service 계층의 리졸버가 갖는다.
 */
public enum VisibilityScope {
    // 전사
    COMPANY,
    // 소속 경로상 최상위 부문 subtree
    DIVISION,
    // 소속 노드 subtree (하위 조직 포함)
    TEAM,
    // 본인만
    SELF
}
