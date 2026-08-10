package kr.proten.pmsmock.mcp;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import kr.proten.pmsmock.port.PersonQueryService;
import kr.proten.pmsmock.port.dto.PersonSummary;
import kr.proten.pmsmock.port.dto.WhoamiResult;

/** FR-AI-13 · FR-AI-16 */
@Component
public class PersonTools {

    private final PersonQueryService people;
    private final CallerContext caller;

    public PersonTools(PersonQueryService people, CallerContext caller) {
        this.people = people;
        this.caller = caller;
    }

    @McpTool(name = "whoami", description = """
            현재 사용자 본인의 id·이름·팀·부문·권한 그룹명을 반환한다.
            "나", "내" 같은 표현의 대상을 확정하거나, 본인 id가 필요할 때 사용한다.
            가동률 본인 조회는 이 도구 없이 get_utilization(scope=ME)로 바로 가능하다.""")
    public WhoamiResult whoami() {
        return people.whoami(caller.callerId());
    }

    @McpTool(name = "find_person", description = """
            이름 또는 팀으로 사람을 검색해 id·이름·팀·직급 목록을 반환한다.
            다른 도구에 personId가 필요한데 id를 모를 때 사용한다.
            조회 가능한 범위(가시성)는 서버가 판정한다.""")
    public List<PersonSummary> findPerson(
            @McpToolParam(description = "이름(부분 일치)", required = false) String name,
            @McpToolParam(description = "팀 이름(부분 일치)", required = false) String team) {
        return people.findPeople(caller.callerId(), name, team);
    }
}
