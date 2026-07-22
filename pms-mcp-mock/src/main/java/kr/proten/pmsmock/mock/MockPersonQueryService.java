package kr.proten.pmsmock.mock;

import java.util.List;
import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.port.PersonQueryPort;
import kr.proten.pmsmock.port.PersonSummaryDto;
import org.springframework.stereotype.Component;

/**
 * 사람 검색 인메모리 구현 — PMS 구현 후 폐기한다.
 */
@Component
public class MockPersonQueryService implements PersonQueryPort {
    @Override
    public List<PersonSummaryDto> findVisible(String name, String team) {
        return MockData.PEOPLE.stream()
                .filter(p -> name == null || p.name().contains(name))
                .filter(p -> team == null || p.team().equals(team))
                .map(p -> new PersonSummaryDto(p.id(), p.name(), p.team(), p.grade()))
                .toList();
    }
}
