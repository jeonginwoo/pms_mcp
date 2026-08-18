package kr.proten.pms.identity.internal.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * reference/seed/people.json 한 행 — 적재 입력 전용 (부록 B).
 * orgRole·grade·team·division은 적재 시 각각 권한 그룹·직급·조직 노드로 해석된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SeedPersonRow(
        long id,
        String name,
        String grade,
        String email,
        String team,
        String division,
        String orgRole,
        double gradeCoeff) {
}
