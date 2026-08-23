package kr.proten.pms.mcp.internal;

import java.util.List;
import kr.proten.pms.mcp.internal.dto.PersonSummary;
import kr.proten.pms.mcp.internal.dto.WhoamiResult;
import kr.proten.pms.person.PersonIdentity;
import kr.proten.pms.person.PersonLookupService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * FR-AI-13 · FR-AI-16 — person 모듈 실연결분.
 * description은 모델이 읽는 문서(구현_노트 §5)로 B2-1 실험에서 확정된 카탈로그 문구다 —
 * 변경은 공용 결정 기록 경유.
 */
@Component
public class PersonTools {

    private final PersonLookupService people;
    private final CallerContext caller;

    public PersonTools(PersonLookupService people, CallerContext caller) {
        this.people = people;
        this.caller = caller;
    }

    @McpTool(name = "whoami", description = """
            현재 사용자 본인의 id·이름·팀·부문·권한 그룹명을 반환한다.
            "나", "내" 같은 표현의 대상을 확정하거나, 본인 id가 필요할 때 사용한다.
            가동률 본인 조회는 이 도구 없이 get_utilization(scope=ME)로 바로 가능하다.""")
    public WhoamiResult whoami() {
        PersonIdentity me = ToolCalls.translating(() -> people.identityOf(caller.callerId()));

        return new WhoamiResult(
                me.id().intValue(), me.name(), me.team(), me.division(), me.permissionGroup());
    }

    @McpTool(name = "find_person", description = """
            이름 또는 팀으로 사람을 검색해 id·이름·팀·직급 목록을 반환한다.
            다른 도구에 personId가 필요한데 id를 모를 때 사용한다.
            조회 가능한 범위(가시성)는 서버가 판정한다.""")
    public List<PersonSummary> findPerson(
            @McpToolParam(description = "이름(부분 일치)", required = false) String name,
            @McpToolParam(description = "팀 이름(부분 일치)", required = false) String team) {
        return ToolCalls.translating(() -> people.search(caller.callerId(), name, team)).stream()
                .map(person -> new PersonSummary(
                        person.id().intValue(), person.name(), person.orgUnit(), person.grade()))
                .toList();
    }
}
