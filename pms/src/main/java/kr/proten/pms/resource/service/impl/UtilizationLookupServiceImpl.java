package kr.proten.pms.resource.service.impl;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.WorkforceDirectoryService;
import kr.proten.pms.person.WorkforceProfile;
import kr.proten.pms.resource.OverbookedBrief;
import kr.proten.pms.resource.UtilizationBrief;
import kr.proten.pms.resource.UtilizationLookupService;
import kr.proten.pms.resource.UtilizationScope;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UtilizationLookupService} 구현 — 하는 일은 <b>범위 해석과 표현 변환 두 가지</b>다.
 *
 * <p>수치는 {@link UtilizationCalculator}가 낸다. 여기서 다시 계산하지 않는 이유는
 * `ProjectLookupServiceImpl`이 가시성을 다시 계산하지 않는 것과 같다: 두 곳에서 세면
 * 챗과 화면의 가동률이 서로 다를 수 있고, 그 차이는 값이 틀린 순간까지 드러나지 않는다.
 *
 * <p><b>범위 해석이 이 클래스의 본체</b>다. 화자만 아는 호출자(챗)의 `scope`를 내부 조회
 * 조건으로 옮긴다 — 그리고 그 조건은 웹이 쓰는 것과 <b>같은</b>
 * {@link UtilizationQuery}다. 그래서 "내 팀 가동률"과 화면에서 자기 팀 노드를 고른 결과가
 * 같은 판정을 탄다.
 */
@Service
@Transactional(readOnly = true)
class UtilizationLookupServiceImpl implements UtilizationLookupService {
    private final UtilizationCalculator calculator;
    private final WorkforceDirectoryService workforceDirectory;

    UtilizationLookupServiceImpl(
            UtilizationCalculator calculator, WorkforceDirectoryService workforceDirectory) {
        this.calculator = calculator;
        this.workforceDirectory = workforceDirectory;
    }

    @Override
    public List<UtilizationBrief> find(
            long callerPersonId, YearMonth month, UtilizationScope scope, Long targetPersonId) {
        UtilizationQuery query = queryOf(callerPersonId, month, scope, targetPersonId);

        return calculator.calculate(callerPersonId, query).stream()
                .map(UtilizationLookupServiceImpl::toBrief)
                .toList();
    }

    @Override
    public List<OverbookedBrief> findOverbooked(long callerPersonId, YearMonth month) {
        // 범위 상한은 화자의 가시성이다 — 도구가 월만 받는다(FR-AI-12). COMPANY와 같은 조건.
        UtilizationQuery query = new UtilizationQuery(month, null, null, true);

        return calculator.calculate(callerPersonId, query).stream()
                .map(UtilizationLookupServiceImpl::toOverbooked)
                .toList();
    }

    /**
     * `scope` → 내부 조회 조건.
     *
     * <p>{@code COMPANY}가 "조직 지정 없음"인 것이 핵심이다: 조건에 조직을 비워 두면
     * 모집단은 화자의 가시성 전체가 되고, 그것이 곧 도구 문구의 "조회 가능한 범위는
     * 서버가 판정한다"다. 전사를 뜻하는 별도 플래그를 만들면 관리자가 아닌 화자에게
     * "전사라고 물었는데 일부만 왔다"를 설명할 방법이 없어진다.
     */
    private UtilizationQuery queryOf(
            long callerPersonId, YearMonth month, UtilizationScope scope, Long targetPersonId) {
        if (scope == null) {
            throw new ValidationException("조회 범위는 필수입니다", "scope");
        }

        return switch (scope) {
            case ME -> new UtilizationQuery(month, callerPersonId, null, false);
            case PERSON -> new UtilizationQuery(month, requireTarget(targetPersonId), null, false);
            case COMPANY -> new UtilizationQuery(month, null, null, false);
            case MY_TEAM ->
                new UtilizationQuery(month, null, callerOrg(callerPersonId).teamOrgUnitId(), false);
            case DIVISION ->
                new UtilizationQuery(
                        month, null, callerOrg(callerPersonId).divisionOrgUnitId(), false);
        };
    }

    /**
     * 화자의 소속 — `MY_TEAM`·`DIVISION`을 조직 id로 옮기는 유일한 경로다.
     *
     * <p>이름으로 되찾는 우회는 쓸 수 없다: `org_units.name`에 유니크 제약이 없어(V1)
     * 같은 이름의 팀이 둘이면 어느 쪽인지 정할 수 없다. 그래서 `WorkforceProfile`이
     * 조직 id 2종을 싣는다(2026-08-23).
     *
     * <p>화자 자신이 없으면 404다 — 인증을 통과한 호출자에게는 일어나지 않아야 하는
     * 일이고, 일어났다면 그것이 조직 정보의 부재이지 범위 해석의 실패가 아니다.
     */
    private WorkforceProfile callerOrg(long callerPersonId) {
        return workforceDirectory.findProfiles(Set.of(callerPersonId)).stream()
                .findFirst()
                .orElseThrow(NotFoundException::new);
    }

    private static long requireTarget(Long targetPersonId) {
        if (targetPersonId == null) {
            throw new ValidationException("scope=PERSON일 때는 personId가 필요합니다", "personId");
        }

        return targetPersonId;
    }

    private static UtilizationBrief toBrief(PersonUtilization row) {
        return new UtilizationBrief(
                row.profile().personId(),
                row.profile().name(),
                row.profile().team(),
                row.profile().division(),
                row.month(),
                row.assignedMm(),
                row.availableMm(),
                row.basicPct(),
                row.adjustedPct());
    }

    private static OverbookedBrief toOverbooked(PersonUtilization row) {
        return new OverbookedBrief(
                row.profile().personId(),
                row.profile().name(),
                row.profile().team(),
                row.basicPct(),
                row.shares().stream()
                        .map(share -> new OverbookedBrief.Cause(share.projectName(), share.mm()))
                        .toList());
    }
}
