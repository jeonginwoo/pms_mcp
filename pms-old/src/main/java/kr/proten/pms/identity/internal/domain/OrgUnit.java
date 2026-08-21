package kr.proten.pms.identity.internal.domain;

/**
 * 조직 노드 — 회사(root)→부문→팀→임의 깊이 트리 (PRD-pms §4, 2026-08-09 ⑧).
 * "부문"·"팀"은 트리 상 위치의 파생 개념이며 별도 타입을 두지 않는다.
 *
 * @param parentId 상위 노드 id — 회사(root)만 null
 */
public record OrgUnit(Long id, Long parentId, String name, long version) {

    /** 회사(root) 노드 여부. */
    public boolean isRoot() {
        return parentId == null;
    }
}
