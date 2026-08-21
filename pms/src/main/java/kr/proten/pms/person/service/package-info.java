/**
 * person 모듈의 서비스 계층 — 유스케이스 계약(인터페이스).
 * 구현은 impl, 입출력 값은 dto, 영속 모델은 entity에 있다.
 *
 * 이 패키지는 모듈 공개 API다 — 다른 모듈은 여기 인터페이스와 dto만 참조한다.
 */
@NamedInterface("service")
package kr.proten.pms.person.service;

import org.springframework.modulith.NamedInterface;
