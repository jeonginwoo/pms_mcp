package kr.proten.pmsmock.mcp;

import java.util.List;
import kr.proten.pmsmock.port.PersonQueryPort;
import kr.proten.pmsmock.port.PersonSummaryDto;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 인물 검색 MCP 도구 — 실전 PMS의 identity 모듈 internal/web으로 그대로 승격한다.
 * whoami는 M0(보안 체인) 범위라 목업에 없다.
 */
@Component
public class IdentityMcpTools {
    // 사람 검색 포트 (실전에서는 application 서비스)
    private final PersonQueryPort personQuery;

    public IdentityMcpTools(PersonQueryPort personQuery) {
        this.personQuery = personQuery;
    }

    @McpTool(name = "find_person",
             description = "이름 또는 팀명으로 사람을 찾는다. 가동률 조회 등 후속 도구에 쓸 personId 확보용")
    public List<PersonSummaryDto> findPerson(
            @McpToolParam(description = "이름 (부분 일치)", required = false) String name,
            @McpToolParam(description = "팀명", required = false) String team) {
        return personQuery.findVisible(name, team);
    }
}
