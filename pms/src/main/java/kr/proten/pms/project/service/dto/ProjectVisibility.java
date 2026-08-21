package kr.proten.pms.project.service.dto;

import java.util.Set;

/**
 * 프로젝트 가시성 판정 결과 (상위 PRD §4-4).
 * 조직 가시성을 프로젝트 축으로 옮긴 값이며, 본인 배정 프로젝트는 조직 밖이어도
 * 포함된다 — 조직 가시성이 본인을 항상 포함하기 때문이다.
 */
public record ProjectVisibility(boolean unrestricted, Set<Long> visibleProjectIds) {

    /**
     * 전사 가시성 — 프로젝트 id 집합을 계산하지 않는다.
     * 이름이 unrestricted가 아닌 이유: 레코드 접근자와 서명이 겹친다.
     */
    public static ProjectVisibility all() {
        return new ProjectVisibility(true, Set.of());
    }

    public static ProjectVisibility of(Set<Long> visibleProjectIds) {
        return new ProjectVisibility(false, Set.copyOf(visibleProjectIds));
    }

    public boolean canView(long projectId) {
        return unrestricted || visibleProjectIds.contains(projectId);
    }
}
