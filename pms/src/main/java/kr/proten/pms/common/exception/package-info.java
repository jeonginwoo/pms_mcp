/**
 * 공용 예외와 에러 봉투 — PRD-pms §7 에러 표의 (HTTP 상태, code)를 담는 예외 타입과
 * 그것을 봉투로 바꾸는 단일 변환 지점(GlobalExceptionHandler).
 *
 * 모든 모듈이 이 예외들을 던지므로 모듈 공개 API다.
 */
@NamedInterface("exception")
package kr.proten.pms.common.exception;

import org.springframework.modulith.NamedInterface;
