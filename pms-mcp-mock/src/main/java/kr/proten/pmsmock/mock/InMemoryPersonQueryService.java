package kr.proten.pmsmock.mock;

import java.util.List;

import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.model.Person;
import kr.proten.pmsmock.port.PersonQueryService;
import kr.proten.pmsmock.port.dto.PersonSummary;
import kr.proten.pmsmock.port.dto.WhoamiResult;

public class InMemoryPersonQueryService implements PersonQueryService {

    private final MockData data;
    private final VisibilityPolicy visibility;

    public InMemoryPersonQueryService(MockData data, VisibilityPolicy visibility) {
        this.data = data;
        this.visibility = visibility;
    }

    @Override
    public WhoamiResult whoami(int callerId) {
        Person me = data.person(callerId);
        return new WhoamiResult(me.id(), me.name(), me.team(), me.division(), me.groupName());
    }

    @Override
    public List<PersonSummary> findPeople(int callerId, String name, String team) {
        Person caller = data.person(callerId);
        return data.people.stream()
                .filter(p -> visibility.canSeePerson(caller, p))
                .filter(p -> name == null || name.isBlank() || p.name().contains(name.trim()))
                .filter(p -> team == null || team.isBlank() || p.team().contains(team.trim()))
                .map(p -> new PersonSummary(p.id(), p.name(), p.team(), p.grade()))
                .toList();
    }
}
