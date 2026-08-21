/**
 * person 모듈의 입출력 값 — 서비스 계약이 주고받는 형태.
 * 엔티티를 계층 밖으로 내보내지 않으려는 규칙(conventions/java-spring.md §4)의 경계다.
 *
 * 이 패키지는 모듈 공개 API다 — 서비스 인터페이스가 이 값들을 반환하므로 함께 열어야 한다.
 */
@NamedInterface("dto")
package kr.proten.pms.person.service.dto;

import org.springframework.modulith.NamedInterface;
