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

    /**
     * 집계 모집단이 될 수 있는 인원 전체 — 재직자, 시스템 계정 제외
     * (2026-08-23 추가, 공용 결정 기록).
     *
     * <p>필요한 이유: 전사 scope 화자의 가시성은 {@link OrgVisibility#unrestricted()}이고
     * 그때 {@code visiblePersonIds}가 <b>빈 집합</b>이다("제약이 없다"는 뜻이지 "아무도
     * 없다"가 아니다). 그러면 집계 호출자에게 명단을 얻을 경로가 없다 — person 밖으로
     * 전원을 내주는 창구가 여기 말고 없었다.
     *
     * <p><b>재직자만</b>이다(사용자 결정 2026-08-23): {@link #findPersonIdsInSubtree}와
     * 같은 규칙이다. "지금 우리 조직 가동률"에 퇴사자를 세면 집계가 틀어진다. 대가는
     * 지난달을 오늘 조회하면 그 사이 퇴사한 사람의 배정이 빠지는 것이고, 그것은
     * 미해결로 등재했다(PRD-pms §12) — {@link #findProfiles}가 비활성을 포함하는 것과
     * 규칙이 다른 것도 그 항목에 함께 적혀 있다.
     *
     * <p>시스템 계정을 빼는 것은 시드가 정한 것이다 — {@code system_account=true}는
     * "인력·가동률·배정 목록에서 제외"다. billable=false라 집계에서는 어차피 빠지지만,
     * 그것과 별개로 명단 자체에 들어가지 않는 편이 규칙 하나로 끝난다.
     */
    Set<Long> findAllAggregatablePersonIds();
}
