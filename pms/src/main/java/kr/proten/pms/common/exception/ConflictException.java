package kr.proten.pms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 — 현재 상태와 충돌하는 요청 (PRD-pms §7: DUPLICATE_NAME ·
 * PROJECT_COMPLETED · INVALID_TRANSITION 등). 낙관적 락 충돌은 최신 값을 함께
 * 알려야 하므로 {@link StaleVersionException}이 따로 있다.
 */
public class ConflictException extends ApiException {
    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
