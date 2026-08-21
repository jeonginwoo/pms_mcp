package kr.proten.pms.person.service.dto;

import java.util.HashSet;
import java.util.Set;

/**
 * 조직 가시성 판정 결과 (상위 PRD §4-4).
 *
 * 판정이 끝난 상태로 모듈 경계를 넘는다 — 대상의 조직을 모르는 project 모듈이
 * 인원 id만으로 가시성을 물을 수 있어야 하기 때문이다. scope 해석·조직 트리
 * 탐색은 person 모듈 안에서 이미 끝나 있다.
 *
 * @param unrestricted      전사 scope — 인원 집합과 무관하게 전부 보인다
 * @param visiblePersonIds  제한 scope에서 보이는 인원 id (본인 포함)
 */
public record OrgVisibility(long callerPersonId, boolean unrestricted, Set<Long> visiblePersonIds) {

    /** 전사 scope — 조직 제약이 없다. */
    public static OrgVisibility unrestricted(long callerPersonId) {
        return new OrgVisibility(callerPersonId, true, Set.of());
    }

    /** 제한 scope — 본인은 scope와 무관하게 항상 보이므로 집합에 함께 넣는다. */
    public static OrgVisibility of(long callerPersonId, Set<Long> visiblePersonIds) {
        Set<Long> withSelf = new HashSet<>(visiblePersonIds);
        withSelf.add(callerPersonId);

        return new OrgVisibility(callerPersonId, false, Set.copyOf(withSelf));
    }

    /** 대상 인원이 조직 가시성 안에 있는가. */
    public boolean canView(long personId) {
        return unrestricted || visiblePersonIds.contains(personId);
    }
}
