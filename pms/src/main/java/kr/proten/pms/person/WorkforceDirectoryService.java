package kr.proten.pms.person;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 인력 속성·조직 소속 조회 — 가동률(EPIC C)을 위해 person이 여는 계약
 * (2026-08-23 신설, 공용 결정 기록).
 *
 * <p>기존 {@link PersonDirectoryService}에 얹지 않고 나눈 이유는 소비자가 다르기
 * 때문이다: 그쪽은 project가 배정에 사람 이름을 붙이려고 쓰고, 이쪽은 resource가
 * 분모·모집단·계수를 얻으려고 쓴다. 한 계약으로 합치면 project가 쓰지 않는
 * capacity·billable에까지 의존하게 된다(conventions §5 ISP의 실제 목적).
 */
public interface WorkforceDirectoryService {
    /**
     * 인원별 가동률 속성 — 없는 id는 결과에서 빠진다(예외가 아니다).
     * 비활성 인원도 돌려준다: 과거 월의 가동률은 그때 재직 중이던 사람으로 계산된다.
     *
     * @param personIds 빈 집합이면 빈 목록 — 질의하지 않는다
     */
    List<WorkforceProfile> findProfiles(Collection<Long> personIds);

    /**
     * 이 조직 노드와 그 하위 전체에 속한 인원 id (AC C1-1 {@code ?orgUnitId=} ·
     * E3-4 subtree 규칙과 같은 트리 해석).
     *
     * <p>가시성은 여기서 적용하지 않는다 — 호출자가 {@link OrgVisibilityService}로
     * 얻은 범위와 교집합한다. 두 곳에서 거르면 어느 쪽이 정본인지 모르게 된다.
     */
    Set<Long> findPersonIdsInSubtree(long orgUnitId);
}
