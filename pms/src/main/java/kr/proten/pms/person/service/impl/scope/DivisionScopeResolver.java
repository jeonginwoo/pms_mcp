package kr.proten.pms.person.service.impl.scope;

import java.util.Set;
import kr.proten.pms.person.service.entity.OrgTree;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.springframework.stereotype.Component;

/**
 * 부문 scope — 소속 경로상 최상위 부문의 subtree (상위 PRD §4-3).
 */
@Component
public class DivisionScopeResolver implements OrgScopeResolver {
    @Override
    public VisibilityScope supports() {
        return VisibilityScope.DIVISION;
    }

    @Override
    public Set<Long> visibleOrgUnitIds(Person caller, OrgTree tree) {
        return tree.subtreeIds(tree.topDivisionIdOf(caller.getOrgUnitId()));
    }
}
