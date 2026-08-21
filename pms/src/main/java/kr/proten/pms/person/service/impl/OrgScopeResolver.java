package kr.proten.pms.person.service.impl;

import java.util.Set;
import kr.proten.pms.person.service.entity.OrgTree;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.VisibilityScope;

/**
 * 가시성 scope 하나의 조직 집합 해석 (상위 PRD §4-3 scope 4단).
 *
 * scope별 분기를 한 곳의 switch로 두지 않고 구현 하나씩 나눈 이유: scope는 권한
 * 그룹 편집으로 의미가 달라지는 축이라 앞으로 늘어날 수 있고, 그때 기존 판정
 * 코드를 건드리지 않고 구현만 추가할 수 있어야 한다(OCP).
 */
public interface OrgScopeResolver {

    /** 이 리졸버가 담당하는 scope. */
    VisibilityScope supports();

    /** 조직 제약이 없는 scope인가 — 전사만 true. */
    default boolean unrestricted() {
        return false;
    }

    /** 이 scope에서 보이는 조직 노드 id — 제약이 없거나 본인뿐이면 빈 집합. */
    Set<Long> visibleOrgUnitIds(Person caller, OrgTree tree);
}
