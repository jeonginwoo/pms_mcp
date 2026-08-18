package kr.proten.pms.identity.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 인원 가시성 판정 단위 테스트 — 권한 그룹 scope 4단 (상위 PRD §4-3·§4-4).
 * 트리: 프로텐(1) → 솔루션사업부(2) → SI팀(3) → SI-1파트(4) · CS팀(5) / AX사업기획부(6)
 */
class PersonVisibilityTest {
    private final OrgTree tree = OrgTree.of(List.of(
            new OrgUnit(1L, null, "프로텐", 0L),
            new OrgUnit(2L, 1L, "솔루션사업부", 0L),
            new OrgUnit(3L, 2L, "SI팀", 0L),
            new OrgUnit(4L, 3L, "SI-1파트", 0L),
            new OrgUnit(5L, 2L, "CS팀", 0L),
            new OrgUnit(6L, 1L, "AX사업기획부", 0L)));

    // 판정 대상 인원 — 조직 배치만 의미 있음
    private final Person divisionHead = person(101L, 2L);
    private final Person siLead = person(102L, 3L);
    private final Person siMember = person(103L, 3L);
    private final Person partMember = person(104L, 4L);
    private final Person csMember = person(105L, 5L);
    private final Person axMember = person(106L, 6L);

    private Person person(Long id, Long orgUnitId) {
        return new Person(id, "p" + id, orgUnitId, 1L, 1L, 1.0, true, false, true, 0L);
    }

    private PersonVisibility visibilityOf(Person caller, VisibilityScope scope) {
        PermissionGroup group = new PermissionGroup(
                1L, "그룹", scope, false, false, false, false, false, 0L);

        return PersonVisibility.of(caller, group, tree);
    }

    @Test
    @DisplayName("COMPANY — 타부문 인원까지 전부 보인다")
    void company_seesEveryone() {
        PersonVisibility visibility = visibilityOf(person(100L, 1L), VisibilityScope.COMPANY);

        assertThat(visibility.canView(axMember)).isTrue();
        assertThat(visibility.canView(partMember)).isTrue();
    }

    @Test
    @DisplayName("DIVISION — 자기 부문 subtree만, 타부문은 안 보인다")
    void division_seesOwnDivisionSubtreeOnly() {
        PersonVisibility visibility = visibilityOf(divisionHead, VisibilityScope.DIVISION);

        assertThat(visibility.canView(siMember)).isTrue();
        assertThat(visibility.canView(partMember)).isTrue();
        assertThat(visibility.canView(csMember)).isTrue();
        assertThat(visibility.canView(axMember)).isFalse();
    }

    @Test
    @DisplayName("DIVISION — 팀 소속 호출자도 부문 전체가 보인다 (경로상 최상위 부문)")
    void division_callerSeatedInTeam_seesWholeDivision() {
        PersonVisibility visibility = visibilityOf(siMember, VisibilityScope.DIVISION);

        assertThat(visibility.canView(csMember)).isTrue();
        assertThat(visibility.canView(axMember)).isFalse();
    }

    @Test
    @DisplayName("TEAM — 소속 노드 subtree(하위 조직 포함, E3-4)만 보인다")
    void team_seesSubtreeIncludingLowerOrg() {
        PersonVisibility visibility = visibilityOf(siLead, VisibilityScope.TEAM);

        assertThat(visibility.canView(siMember)).isTrue();
        assertThat(visibility.canView(partMember)).isTrue();
        assertThat(visibility.canView(csMember)).isFalse();
        assertThat(visibility.canView(divisionHead)).isFalse();
    }

    @Test
    @DisplayName("SELF — 본인만 보인다")
    void self_seesOnlySelf() {
        PersonVisibility visibility = visibilityOf(siMember, VisibilityScope.SELF);

        assertThat(visibility.canView(siMember)).isTrue();
        assertThat(visibility.canView(siLead)).isFalse();
    }
}
