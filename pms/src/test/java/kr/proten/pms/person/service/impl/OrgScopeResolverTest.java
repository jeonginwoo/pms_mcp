package kr.proten.pms.person.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.proten.pms.person.service.entity.OrgTree;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 가시성 scope별 조직 집합 해석 단위 테스트 (상위 PRD §4-3 scope 4단).
 * scope 하나 = 리졸버 하나이므로 scope 추가는 클래스 추가로만 끝난다(OCP) —
 * 이 테스트는 4종이 각자 자기 scope만 응답한다는 계약을 고정한다.
 */
class OrgScopeResolverTest {
    private final OrgTree tree = PersonFixtures.orgTree();
    // SI팀 소속 호출자 — 팀 scope는 SI팀 subtree, 부문 scope는 솔루션사업부 subtree
    private final Person caller = PersonFixtures.person(101L, "호출자", PersonFixtures.SI_TEAM_ID, 1L);

    @Test
    @DisplayName("전사 — 조직 제약이 없다(unrestricted)")
    void company_isUnrestricted() {
        OrgScopeResolver resolver = new CompanyScopeResolver();

        assertThat(resolver.supports()).isEqualTo(VisibilityScope.COMPANY);
        assertThat(resolver.unrestricted()).isTrue();
        assertThat(resolver.visibleOrgUnitIds(caller, tree)).isEmpty();
    }

    @Test
    @DisplayName("부문 — 소속 경로상 최상위 부문의 subtree")
    void division_resolvesTopDivisionSubtree() {
        OrgScopeResolver resolver = new DivisionScopeResolver();

        assertThat(resolver.supports()).isEqualTo(VisibilityScope.DIVISION);
        assertThat(resolver.unrestricted()).isFalse();
        assertThat(resolver.visibleOrgUnitIds(caller, tree))
                .containsExactlyInAnyOrder(2L, 3L, 4L, 5L);
    }

    @Test
    @DisplayName("팀 — 소속 노드의 subtree(하위 조직 포함)")
    void team_resolvesOwnSubtree() {
        OrgScopeResolver resolver = new TeamScopeResolver();

        assertThat(resolver.supports()).isEqualTo(VisibilityScope.TEAM);
        assertThat(resolver.visibleOrgUnitIds(caller, tree))
                .containsExactlyInAnyOrder(PersonFixtures.SI_TEAM_ID, PersonFixtures.SI_PART_ID);
    }

    @Test
    @DisplayName("본인 — 조직 집합이 비어 있다(본인 판정은 VO가 담당)")
    void self_resolvesNoOrgUnit() {
        OrgScopeResolver resolver = new SelfScopeResolver();

        assertThat(resolver.supports()).isEqualTo(VisibilityScope.SELF);
        assertThat(resolver.unrestricted()).isFalse();
        assertThat(resolver.visibleOrgUnitIds(caller, tree)).isEmpty();
    }

    @Test
    @DisplayName("scope 4단 전부에 리졸버가 있다 — 누락 시 판정 불능")
    void resolvers_coverEveryScope() {
        List<OrgScopeResolver> resolvers = List.of(
                new CompanyScopeResolver(),
                new DivisionScopeResolver(),
                new TeamScopeResolver(),
                new SelfScopeResolver());

        assertThat(resolvers).map(OrgScopeResolver::supports)
                .containsExactlyInAnyOrder(VisibilityScope.values());
    }
}
