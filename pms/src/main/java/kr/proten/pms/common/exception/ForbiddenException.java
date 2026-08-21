package kr.proten.pms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 403 — 가시성 안이지만 그 행위의 권한이 없을 때 (PRD-pms §7 FORBIDDEN).
 * 가시성 밖이면 이 예외가 아니라 {@link NotFoundException}이다 — 둘의 구분이
 * 호출자에게 새면 404 은닉이 무너진다.
 */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
