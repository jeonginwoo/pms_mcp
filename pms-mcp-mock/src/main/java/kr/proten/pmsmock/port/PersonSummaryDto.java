package kr.proten.pmsmock.port;

/**
 * 사람 요약 응답. 후속 도구 호출에 쓸 personId 확보용(FR-AI-13)이라 id·이름·팀·직급만 담는다.
 */
public record PersonSummaryDto(long id, String name, String team, String grade) {}
