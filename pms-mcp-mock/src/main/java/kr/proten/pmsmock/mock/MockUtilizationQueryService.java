package kr.proten.pmsmock.mock;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.port.CauseDto;
import kr.proten.pmsmock.port.OverbookingDto;
import kr.proten.pmsmock.port.UtilizationEntryDto;
import kr.proten.pmsmock.port.UtilizationQueryPort;
import kr.proten.pmsmock.port.UtilizationReportDto;
import org.springframework.stereotype.Component;

/**
 * 가동률 조회 인메모리 구현 — PMS 구현 후 폐기한다.
 * B-0 단계엔 인증이 없어 MY_TEAM/DIVISION의 기준자를 MockData.DEFAULT_REQUESTER_ID로 고정한다.
 */
@Component
public class MockUtilizationQueryService implements UtilizationQueryPort {
    // 허용 scope 값 — 실전에서는 enum으로 승격
    private static final Set<String> SCOPES = Set.of("MY_TEAM", "DIVISION", "PERSON");

    @Override
    public UtilizationReportDto report(YearMonth month, String scope, Long personId) {
        if (!SCOPES.contains(scope)) {
            throw new IllegalArgumentException("scope는 MY_TEAM, DIVISION, PERSON 중 하나여야 합니다: " + scope);
        }

        if ("PERSON".equals(scope) && personId == null) {
            throw new IllegalArgumentException("scope=PERSON일 때는 personId가 필요합니다");
        }

        List<UtilizationEntryDto> entries = scopePeople(scope, personId).stream()
                .map(p -> entryOf(p, month))
                .sorted(Comparator.comparingInt(UtilizationEntryDto::adjustedRate).reversed())
                .toList();

        return new UtilizationReportDto(month.toString(), scope, entries);
    }

    @Override
    public List<OverbookingDto> findOverbooked(YearMonth month) {
        return MockData.PEOPLE.stream()
                .map(p -> toOverbooking(p, month))
                .filter(o -> o.adjustedRate() > 100)
                .sorted(Comparator.comparingInt(OverbookingDto::adjustedRate).reversed())
                .toList();
    }

    /**
     * scope에 해당하는 인원 집합을 고릅니다. 기준자는 DEFAULT_REQUESTER_ID(B-2에서 SecurityContext로 교체).
     */
    private List<MockData.MockPerson> scopePeople(String scope, Long personId) {
        if ("PERSON".equals(scope)) {
            return MockData.PEOPLE.stream()
                    .filter(p -> p.id() == personId)
                    .toList();
        }

        MockData.MockPerson requester = MockData.PEOPLE.stream()
                .filter(p -> p.id() == MockData.DEFAULT_REQUESTER_ID)
                .findFirst()
                .orElseThrow();

        if ("MY_TEAM".equals(scope)) {
            return MockData.PEOPLE.stream()
                    .filter(p -> p.team().equals(requester.team()))
                    .toList();
        }

        return MockData.PEOPLE.stream()
                .filter(p -> p.division().equals(requester.division()))
                .toList();
    }

    /**
     * 보정 공식: rate = round(mm×100), adjustedRate = round(rate×직급계수). 배정이 없으면 0%.
     */
    private UtilizationEntryDto entryOf(MockData.MockPerson person, YearMonth month) {
        double mm = totalMm(person.id(), month);
        int rate = (int) Math.round(mm * 100);
        int adjustedRate = (int) Math.round(rate * person.coeff());

        return new UtilizationEntryDto(person.id(), person.name(), person.team(),
                roundMm(mm), rate, adjustedRate);
    }

    private OverbookingDto toOverbooking(MockData.MockPerson person, YearMonth month) {
        UtilizationEntryDto entry = entryOf(person, month);
        List<CauseDto> causes = MockData.ASSIGNMENTS.stream()
                .filter(a -> a.personId() == person.id() && a.month().equals(month.toString()))
                .map(a -> new CauseDto(projectName(a.projectId()), a.mm()))
                .toList();

        return new OverbookingDto(person.id(), person.name(), person.team(), month.toString(),
                entry.mm(), entry.rate(), entry.adjustedRate(), causes);
    }

    private double totalMm(long personId, YearMonth month) {
        return MockData.ASSIGNMENTS.stream()
                .filter(a -> a.personId() == personId && a.month().equals(month.toString()))
                .mapToDouble(MockData.MockAssignment::mm)
                .sum();
    }

    private String projectName(long projectId) {
        return MockData.PROJECTS.stream()
                .filter(p -> p.id() == projectId)
                .map(MockData.MockProject::name)
                .findFirst()
                .orElse("(미상)");
    }

    /**
     * 부동소수 합산 오차를 소수 1자리로 정리합니다 (0.4+0.3=0.7000...1 방지).
     */
    private double roundMm(double mm) {
        return Math.round(mm * 10) / 10.0;
    }
}
