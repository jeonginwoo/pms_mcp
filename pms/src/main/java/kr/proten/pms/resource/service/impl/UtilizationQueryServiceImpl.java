package kr.proten.pms.resource.service.impl;

import java.util.List;
import kr.proten.pms.resource.service.UtilizationQueryService;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import kr.proten.pms.resource.service.dto.UtilizationView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가동률 조회 — 웹(`GET /api/utilization`)이 부르는 유스케이스 (EPIC C).
 *
 * <p>수치는 {@link UtilizationCalculator}가 낸다. 여기 남는 것은 <b>웹이 무엇을 받는가</b>
 * 하나다: 프로젝트별 기여분({@code shares})을 버린다. 부록 A의 `/utilization` 화면은
 * 원인을 쓰지 않으므로 44명분 응답에 안 쓰는 목록을 실어 보내지 않는다 — 그것을 원하는
 * 호출자는 {@code /mcp}의 {@code list_overbooked}뿐이고, 그쪽은 루트 계약으로 받는다.
 */
@Service
@Transactional(readOnly = true)
class UtilizationQueryServiceImpl implements UtilizationQueryService {
    private final UtilizationCalculator calculator;

    UtilizationQueryServiceImpl(UtilizationCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public List<UtilizationView> find(long callerPersonId, UtilizationQuery query) {
        return calculator.calculate(callerPersonId, query).stream()
                .map(UtilizationQueryServiceImpl::toView)
                .toList();
    }

    private static UtilizationView toView(PersonUtilization row) {
        return new UtilizationView(
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
}
