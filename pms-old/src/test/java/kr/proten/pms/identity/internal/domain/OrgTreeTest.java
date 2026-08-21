package kr.proten.pms.identity.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 조직 트리 탐색 단위 테스트 — subtree(임의 깊이)와 최상위 부문 계산.
 * 트리: 프로텐(1) → 솔루션사업부(2) → SI팀(3) → SI-1파트(4) · CS팀(5) / AX사업기획부(6)
 */
class OrgTreeTest {
    private final OrgTree tree = OrgTree.of(List.of(
            new OrgUnit(1L, null, "프로텐", 0L),
            new OrgUnit(2L, 1L, "솔루션사업부", 0L),
            new OrgUnit(3L, 2L, "SI팀", 0L),
            new OrgUnit(4L, 3L, "SI-1파트", 0L),
            new OrgUnit(5L, 2L, "CS팀", 0L),
            new OrgUnit(6L, 1L, "AX사업기획부", 0L)));

    @Test
    @DisplayName("subtree — 자신 + 임의 깊이 하위 전부 (E3-4)")
    void subtreeIds_midNode_includesSelfAndAllDescendants() {
        assertThat(tree.subtreeIds(2L)).containsExactlyInAnyOrder(2L, 3L, 4L, 5L);
        assertThat(tree.subtreeIds(3L)).containsExactlyInAnyOrder(3L, 4L);
    }

    @Test
    @DisplayName("subtree — 리프 노드는 자기 자신만")
    void subtreeIds_leaf_returnsSelfOnly() {
        assertThat(tree.subtreeIds(4L)).containsExactly(4L);
    }

    @Test
    @DisplayName("최상위 부문 — 깊은 노드에서 경로상 root 직계 자식")
    void topDivisionIdOf_deepNode_returnsPathDivision() {
        assertThat(tree.topDivisionIdOf(4L)).isEqualTo(2L);
        assertThat(tree.topDivisionIdOf(5L)).isEqualTo(2L);
    }

    @Test
    @DisplayName("최상위 부문 — 부문 노드 자신은 자신")
    void topDivisionIdOf_divisionNode_returnsItself() {
        assertThat(tree.topDivisionIdOf(2L)).isEqualTo(2L);
    }

    @Test
    @DisplayName("최상위 부문 — root 소속은 root (ASSUMPTION: 전사로 넓힘)")
    void topDivisionIdOf_root_returnsRoot() {
        assertThat(tree.topDivisionIdOf(1L)).isEqualTo(1L);
    }

    @Test
    @DisplayName("미지의 노드 id — 데이터 이상으로 취급")
    void subtreeIds_unknownNode_throws() {
        assertThatIllegalStateException().isThrownBy(() -> tree.subtreeIds(99L));
    }
}
