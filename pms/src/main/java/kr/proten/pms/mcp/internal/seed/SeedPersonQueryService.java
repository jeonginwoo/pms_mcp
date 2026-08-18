package kr.proten.pms.mcp.internal.seed;

import java.util.List;

import kr.proten.pms.mcp.PersonQueryService;
import kr.proten.pms.mcp.PersonSummary;
import kr.proten.pms.mcp.WhoamiResult;

/**
 * PersonQueryService 임시 구현 — 시드 JSON 직접 조회 (게이트 M0의 whoami 관통용).
 * identity 모듈 애플리케이션 서비스가 구현을 넘겨받으면 폐기한다(PMS-M1).
 * 로직은 pms-mcp-mock InMemoryPersonQueryService와 동일.
 */
public class SeedPersonQueryService implements PersonQueryService {

    private final SeedPeople seed;

    public SeedPersonQueryService(SeedPeople seed) {
        this.seed = seed;
    }

    @Override
    public WhoamiResult whoami(int callerId) {
        SeedPerson me = seed.person(callerId);
        return new WhoamiResult(me.id(), me.name(), me.team(), me.division(), me.groupName());
    }

    @Override
    public List<PersonSummary> findPeople(int callerId, String name, String team) {
        SeedPerson caller = seed.person(callerId);
        return seed.all().stream()
                .filter(p -> seed.canSee(caller, p))
                .filter(p -> name == null || name.isBlank() || p.name().contains(name.trim()))
                .filter(p -> team == null || team.isBlank() || p.team().contains(team.trim()))
                .map(p -> new PersonSummary(p.id(), p.name(), p.team(), p.grade()))
                .toList();
    }
}
