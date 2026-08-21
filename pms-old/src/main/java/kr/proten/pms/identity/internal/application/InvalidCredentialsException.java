package kr.proten.pms.identity.internal.application;

import kr.proten.pms.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 로그인 실패 — email 미존재와 비밀번호 불일치를 구분하지 않는다(계정 존재 탐지 방지).
 */
public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "이메일 또는 비밀번호가 올바르지 않습니다");
    }
}
