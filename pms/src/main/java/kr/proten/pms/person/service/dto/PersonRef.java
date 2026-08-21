package kr.proten.pms.person.service.dto;

/**
 * 인원 참조 값 — 모듈 밖으로 나가는 유일한 인원 표현.
 * 엔티티를 노출하지 않으려는 규칙(conventions/java-spring.md §4)의 경계이며,
 * 표시에 필요한 필드만 담는다 — capacity·billable 같은 집계용 속성은 넣지 않는다.
 */
public record PersonRef(Long id, String name, String orgUnit, String grade) {
}
