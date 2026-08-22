package kr.proten.pms.person.service.impl.scope;

import java.util.Set;
import kr.proten.pms.person.service.entity.OrgTree;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.springframework.stereotype.Component;

/** 전사 scope — 조직 제약이 없어 집합을 계산하지 않는다 (상위 PRD §4-3). */
@Component
public class CompanyScopeResolver implements OrgScopeResolver {
    @Override
    public VisibilityScope supports() {
        return VisibilityScope.COMPANY;
    }

    @Override
    public boolean unrestricted() {
        return true;
    }

    @Override
    public Set<Long> visibleOrgUnitIds(Person caller, OrgTree tree) {
        return Set.of();
    }
}
