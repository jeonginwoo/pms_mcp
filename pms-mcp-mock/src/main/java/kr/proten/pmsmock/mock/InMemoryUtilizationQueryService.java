package kr.proten.pmsmock.mock;

import java.util.Comparator;
import java.util.List;

import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.model.Assignment;
import kr.proten.pmsmock.model.Person;
import kr.proten.pmsmock.port.ToolError;
import kr.proten.pmsmock.port.UtilizationQueryService;
import kr.proten.pmsmock.port.dto.OverbookedEntry;
import kr.proten.pmsmock.port.dto.UtilizationEntry;

public class InMemoryUtilizationQueryService implements UtilizationQueryService {

    private static final double CAPACITY_MM = 1.0; // 목업 고정 — 실전 Capacity 규칙은 시드 적재 정책과 함께

    private final MockData data;
    private final VisibilityPolicy visibility;

    public InMemoryUtilizationQueryService(MockData data, VisibilityPolicy visibility) {
        this.data = data;
        this.visibility = visibility;
    }

    @Override
    public List<UtilizationEntry> getUtilization(int callerId, String month, String scope, Integer personId) {
        Person caller = data.person(callerId);
        validateMonth(month);
        return switch (scope) {
            case "ME" -> List.of(entryOf(caller, month));
            case "PERSON" -> {
                if (personId == null) {
                    throw ToolError.validation("scope=PERSON에는 personId가 필요합니다.");
                }
                Person target = data.people.stream()
                        .filter(p -> p.id() == personId).findFirst()
                        .orElseThrow(ToolError::notFound);
                if (!visibility.canSeePerson(caller, target)) {
                    throw ToolError.notFound(); // 404 은닉
                }
                yield List.of(entryOf(target, month));
            }
            case "MY_TEAM" -> {
                if (!visibility.canAggregate(caller, "MY_TEAM")) {
                    throw ToolError.notFound(); // 은닉 — 권한/부재 비구분 (S-4)
                }
                yield aggregate(month, p -> p.team().equals(caller.team()));
            }
            case "DIVISION" -> {
                if (!visibility.canAggregate(caller, "DIVISION")) {
                    throw ToolError.notFound();
                }
                yield aggregate(month, p -> p.division().equals(caller.division()));
            }
            default -> throw ToolError.validation("scope는 ME/MY_TEAM/DIVISION/PERSON 중 하나여야 합니다.");
        };
    }

    @Override
    public List<OverbookedEntry> listOverbooked(int callerId, String month) {
        Person caller = data.person(callerId);
        validateMonth(month);
        // 범위 = 호출자 가시성(서버 판정) ∩ billable=true. 판정 = 기본 가동률 > 100 (2026-08-10 재정의)
        return data.people.stream()
                .filter(p -> visibility.canSeePerson(caller, p))
                .filter(Person::billable)
                .map(p -> entryOf(p, month))
                .filter(e -> e.basicPct() > 100.0)
                .sorted(Comparator.comparingDouble(UtilizationEntry::basicPct).reversed())
                .map(e -> toOverbooked(caller, e, month))
                .toList();
    }

    /** 집계 모집단 = billable=true (상위 PRD §3). 정렬 = 기본 가동률(집계 정본) 오름차순 */
    private List<UtilizationEntry> aggregate(String month, java.util.function.Predicate<Person> in) {
        return data.people.stream()
                .filter(in)
                .filter(Person::billable)
                .map(p -> entryOf(p, month))
                .sorted(Comparator.comparingDouble(UtilizationEntry::basicPct))
                .toList();
    }

    private UtilizationEntry entryOf(Person p, String month) {
        double assigned = data.assignments.stream()
                .filter(a -> a.personId() == p.id() && a.month().equals(month))
                .mapToDouble(Assignment::mm)
                .sum();
        double basic = round1(assigned / CAPACITY_MM * 100);
        // 보정 = Σ(배정MM × 직급계수) ÷ 가용 — 단가 가중 보조 지표 (상위 PRD §3, 2026-08-10 재정의: 구 ÷coeff 폐기)
        double adjusted = round1(assigned * p.gradeCoeff() / CAPACITY_MM * 100);
        return new UtilizationEntry(p.id(), p.name(), month, round1(assigned), CAPACITY_MM, basic, adjusted);
    }

    private OverbookedEntry toOverbooked(Person caller, UtilizationEntry e, String month) {
        // 원인 배정은 프로젝트 수준 데이터 — 호출자의 프로젝트 가시성 밖 건은 제외 (상위 PRD §4-4, 404 은닉 정합)
        List<OverbookedEntry.Cause> causes = data.assignments.stream()
                .filter(a -> a.personId() == e.personId() && a.month().equals(month))
                .filter(a -> visibility.canSeeProject(caller, data.projects.get(a.projectId())))
                .map(a -> new OverbookedEntry.Cause(data.projects.get(a.projectId()).name(), a.mm()))
                .toList();
        Person p = data.person(e.personId());
        return new OverbookedEntry(e.personId(), e.name(), p.team(), e.basicPct(), causes);
    }

    private static void validateMonth(String month) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw ToolError.validation("month는 \"yyyy-MM\" 형식이어야 합니다.");
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
