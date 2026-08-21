package kr.proten.pms.person.service.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 조직 트리 탐색 단위 테스트 — subtree(임의 깊이)와 최상위 부문 계산.
 * 트리 구성은 PersonFixtures 참조.
 */
class OrgTreeTest {
    private final OrgTree tree = PersonFixtures.orgTree();

    @Test
    @DisplayName("subtree — 자신 + 임의 깊이 하위 전부")
    void subtreeIds_midNode_includesSelfAndAllDescendants() {
        assertThat(tree.subtreeIds(PersonFixtures.DIVISION_ID))
                .containsExactlyInAnyOrder(2L, 3L, 4L, 5L);
        assertThat(tree.subtreeIds(PersonFixtures.SI_TEAM_ID))
                .containsExactlyInAnyOrder(3L, 4L);
    }

    @Test
    @DisplayName("subtree — 리프 노드는 자기 자신만")
    void subtreeIds_leaf_returnsSelfOnly() {
        assertThat(tree.subtreeIds(PersonFixtures.SI_PART_ID))
                .containsExactly(PersonFixtures.SI_PART_ID);
    }

    @Test
    @DisplayName("최상위 부문 — 깊은 노드에서 경로상 root 직계 자식")
    void topDivisionIdOf_deepNode_returnsPathDivision() {
        assertThat(tree.topDivisionIdOf(PersonFixtures.SI_PART_ID))
                .isEqualTo(PersonFixtures.DIVISION_ID);
        assertThat(tree.topDivisionIdOf(PersonFixtures.CS_TEAM_ID))
                .isEqualTo(PersonFixtures.DIVISION_ID);
    }

    @Test
    @DisplayName("최상위 부문 — 부문 노드 자신은 자신")
    void topDivisionIdOf_divisionNode_returnsItself() {
        assertThat(tree.topDivisionIdOf(PersonFixtures.DIVISION_ID))
                .isEqualTo(PersonFixtures.DIVISION_ID);
    }

    @Test
    @DisplayName("최상위 부문 — root 소속은 root (ASSUMPTION: 전사로 넓힘)")
    void topDivisionIdOf_root_returnsRoot() {
        assertThat(tree.topDivisionIdOf(PersonFixtures.COMPANY_ID))
                .isEqualTo(PersonFixtures.COMPANY_ID);
    }

    @Test
    @DisplayName("미지의 노드 id — 데이터 이상으로 취급")
    void subtreeIds_unknownNode_throws() {
        assertThatIllegalStateException().isThrownBy(() -> tree.subtreeIds(99L));
    }
}
