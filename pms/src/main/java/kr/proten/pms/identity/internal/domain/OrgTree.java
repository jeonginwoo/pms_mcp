package kr.proten.pms.identity.internal.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 조직 트리 탐색 — 가시성 판정이 쓰는 subtree·부문 계산 (상위 PRD §4-4).
 * 조직 노드는 수십 개 규모라 전체 로드 후 메모리 탐색이 단순·표준(재귀 SQL 불요) —
 * 규모가 커지면 질의 하향을 재검토한다.
 */
public final class OrgTree {
    // id → 노드
    private final Map<Long, OrgUnit> unitsById;
    // 상위 id → 직계 하위 id 목록
    private final Map<Long, List<Long>> childIdsByParentId;

    private OrgTree(Map<Long, OrgUnit> unitsById, Map<Long, List<Long>> childIdsByParentId) {
        this.unitsById = unitsById;
        this.childIdsByParentId = childIdsByParentId;
    }

    public static OrgTree of(List<OrgUnit> units) {
        Map<Long, OrgUnit> byId = new HashMap<>();
        Map<Long, List<Long>> children = new HashMap<>();

        for (OrgUnit unit : units) {
            byId.put(unit.id(), unit);

            if (!unit.isRoot()) {
                children.computeIfAbsent(unit.parentId(), parentId -> new ArrayList<>())
                        .add(unit.id());
            }
        }

        return new OrgTree(byId, children);
    }

    /** 노드 자신 + 모든 하위 조직 id — 임의 깊이 (팀 가시성 subtree, E3-4). */
    public Set<Long> subtreeIds(Long nodeId) {
        requireKnown(nodeId);
        Set<Long> result = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(nodeId);

        while (!stack.isEmpty()) {
            Long current = stack.pop();

            if (!result.add(current)) {
                // 데이터 이상(순환) 방어 — 무한 루프 방지
                continue;
            }

            childIdsByParentId.getOrDefault(current, List.of()).forEach(stack::push);
        }

        return result;
    }

    /**
     * 소속 경로상 최상위 부문(root 직계 자식) id — DIVISION scope의 기준 노드.
     * ASSUMPTION: root 직속 소속(부문 미배속)은 root를 돌려준다 — 부문 scope가
     * 전사 subtree로 넓어지는 쪽이 판정 불능보다 안전하다(시드에 해당 인원은
     * 관리자 그룹뿐이라 실영향 없음).
     */
    public Long topDivisionIdOf(Long nodeId) {
        requireKnown(nodeId);
        Set<Long> visited = new HashSet<>();
        Long current = nodeId;

        while (visited.add(current)) {
            OrgUnit unit = unitsById.get(current);

            if (unit.isRoot()) {
                return unit.id();
            }

            OrgUnit parent = unitsById.get(unit.parentId());

            if (parent == null) {
                throw new IllegalStateException("상위 조직 노드 없음: " + unit.parentId());
            }

            if (parent.isRoot()) {
                return unit.id();
            }

            current = parent.id();
        }

        throw new IllegalStateException("조직 트리 순환 감지: " + nodeId);
    }

    private void requireKnown(Long nodeId) {
        if (!unitsById.containsKey(nodeId)) {
            throw new IllegalStateException("조직 노드 없음: " + nodeId);
        }
    }
}
