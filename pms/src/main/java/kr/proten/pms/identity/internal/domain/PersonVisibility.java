package kr.proten.pms.identity.internal.domain;

import java.util.Set;

/**
 * 호출자 한 명의 인원 가시성 판정자 — 권한 그룹 scope 4단 해석의 유일 지점
 * (상위 PRD §4-3·§4-4: 판정·가시성·404 은닉이 전부 그룹 정의를 따른다).
 * 프로젝트 역할에 의한 확장(프로젝트 컨텍스트 내)은 project 모듈 구현
 * 시(PMS-M2) 합집합으로 얹는다.
 */
public record PersonVisibility(VisibilityScope scope, Set<Long> visibleOrgUnitIds, Long selfPersonId) {

    /** 호출자·그룹·조직 트리로 판정자를 만든다 — scope별 가시 조직 집합을 여기서 확정. */
    public static PersonVisibility of(Person caller, PermissionGroup group, OrgTree tree) {
        Set<Long> orgUnitIds = switch (group.visibilityScope()) {
            // 전사·본인 scope는 조직 집합이 필요 없다
            case COMPANY, SELF -> Set.of();
            // 소속 경로상 최상위 부문의 subtree
            case DIVISION -> tree.subtreeIds(tree.topDivisionIdOf(caller.orgUnitId()));
            // 소속 노드의 subtree — 하위 조직 포함 (E3-4)
            case TEAM -> tree.subtreeIds(caller.orgUnitId());
        };

        return new PersonVisibility(group.visibilityScope(), orgUnitIds, caller.id());
    }

    /** 대상 인원이 보이는가 — 본인은 scope와 무관하게 항상 보인다. */
    public boolean canView(Person target) {
        if (target.id().equals(selfPersonId)) {
            return true;
        }

        return switch (scope) {
            case COMPANY -> true;
            case DIVISION, TEAM -> visibleOrgUnitIds.contains(target.orgUnitId());
            case SELF -> false;
        };
    }
}
