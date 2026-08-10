package kr.proten.pmsmock.model;

/** 시드 people.json 추출본 — orgRole은 기본 그룹 4종으로 적재(결정 ⑦) */
public record Person(
        int id,
        String name,
        String grade,
        String team,
        String division,
        String groupName,
        double gradeCoeff,
        boolean billable) {
}
