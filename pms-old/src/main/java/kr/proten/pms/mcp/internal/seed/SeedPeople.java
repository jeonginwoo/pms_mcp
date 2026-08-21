package kr.proten.pms.mcp.internal.seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import kr.proten.pms.mcp.ToolError;

/**
 * 시드 인력 44명 인메모리 (임시 — pms 트랙의 시드 DB 적재·identity 서비스가
 * 대체한다, PMS-M1). 인력 가시성은 기본 그룹 4단(상위 PRD §4)을 시드
 * orgRole로 판정 — pms-mcp-mock VisibilityPolicy.canSeePerson과 동일 규칙.
 */
public class SeedPeople {

    private final List<SeedPerson> people;

    public SeedPeople(Path peopleJson) {
        try {
            this.people = new ObjectMapper().readValue(
                    Files.readAllBytes(peopleJson), new TypeReference<List<SeedPerson>>() {
                    });
        } catch (IOException | JacksonException e) { // Jackson 3는 언체크 JacksonException
            throw new IllegalStateException(
                    "시드 인력 파일을 읽을 수 없습니다: " + peopleJson.toAbsolutePath()
                            + " — pms.mcp.seed-people-path 확인", e);
        }
    }

    public List<SeedPerson> all() {
        return people;
    }

    /** 부재 id는 404 은닉 — 토큰 sub가 시드에 없는 경우 포함 */
    public SeedPerson person(int id) {
        return people.stream().filter(p -> p.id() == id).findFirst()
                .orElseThrow(ToolError::notFound);
    }

    public boolean canSee(SeedPerson caller, SeedPerson target) {
        return switch (caller.orgRole()) {
            case "ADMIN" -> true;
            case "DIVISION_HEAD" -> target.division().equals(caller.division());
            case "TEAM_LEAD" -> target.team().equals(caller.team());
            default -> target.id() == caller.id();
        };
    }
}
