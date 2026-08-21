package kr.proten.pms.mcp.internal.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * reference/seed/people.json 한 명 (임시 — identity 모듈 적재 후 폐기).
 * groupName은 시드 적재 규칙(2026-08-09 결정 ⑦: orgRole 4종 → 기본 그룹 4종)의
 * 선반영 — 정본은 identity 모듈의 PermissionGroup 데이터가 된다(PMS-M1).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedPerson(int id, String name, String grade, String team, String division, String orgRole) {

    public String groupName() {
        return switch (orgRole) {
            case "ADMIN" -> "관리자";
            case "DIVISION_HEAD" -> "부문장";
            case "TEAM_LEAD" -> "팀장";
            default -> "팀원";
        };
    }
}
