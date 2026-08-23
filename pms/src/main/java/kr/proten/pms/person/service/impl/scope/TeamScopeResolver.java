package kr.proten.pms.person.service.impl.scope;

import java.util.Set;
import kr.proten.pms.person.service.entity.OrgTree;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.springframework.stereotype.Component;

/**
 * 팀 scope — 소속 노드의 subtree, 하위 조직을 포함한다 (상위 PRD §4-3).
 */
@Component
public class TeamScopeResolver implements OrgScopeResolver {
    @Override
    public VisibilityScope supports() {
        return VisibilityScope.TEAM;
    }

    @Override
    public Set<Long> visibleOrgUnitIds(Person caller, OrgTree tree) {
        return tree.subtreeIds(caller.getOrgUnitId());
    }
}
