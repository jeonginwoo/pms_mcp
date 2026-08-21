package kr.proten.pms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 401 UNAUTHENTICATED — 호출자를 식별할 수 없을 때 (PRD-pms §7).
 * 인증이 들어오기 전에는 호출자 식별 헤더의 부재·형식 오류가 이 예외로 수렴한다
 * (2026-08-21 재구축 범위 — 인증 제외).
 */
public class UnauthenticatedException extends ApiException {
    public UnauthenticatedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", message);
    }
}
