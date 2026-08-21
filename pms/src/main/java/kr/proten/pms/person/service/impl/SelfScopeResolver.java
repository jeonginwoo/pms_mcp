package kr.proten.pms.person.service.impl;

import java.util.Set;
import kr.proten.pms.person.service.entity.OrgTree;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.springframework.stereotype.Component;

/**
 * 본인 scope — 조직 단위로 보이는 인원이 없다.
 * 본인 자신은 조직 집합이 아니라 판정 결과 VO가 항상 포함시킨다(OrgVisibility.of).
 */
@Component
class SelfScopeResolver implements OrgScopeResolver {
    @Override
    public VisibilityScope supports() {
        return VisibilityScope.SELF;
    }

    @Override
    public Set<Long> visibleOrgUnitIds(Person caller, OrgTree tree) {
        return Set.of();
    }
}
