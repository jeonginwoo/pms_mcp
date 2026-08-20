/**
 * common — 공용 예외 → HTTP/도구 에러 매핑(RestControllerAdvice + §7 에러 봉투
 * ErrorResponse, 403/404 은닉/409/422 표는 conventions/java-spring.md §4)·공유 VO·
 * 호출자 식별(@CallerPersonId).
 * 도메인 로직 금지 — 모든 모듈이 참조할 수 있는 최소 공통분모만 둔다.
 */
@ApplicationModule
package kr.proten.pms.common;

import org.springframework.modulith.ApplicationModule;
