package kr.proten.pms.common.config;

import kr.proten.pms.common.exception.UnauthenticatedException;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * 인증이 꺼진 동안의 호출자 식별 — `X-Caller-Person-Id` 헤더 (2026-08-21 결정).
 *
 * 헤더를 그대로 신뢰하므로 **이 상태의 앱은 외부에 노출하면 안 된다.**
 * 부재와 비숫자를 같은 401로 수렴시킨다 — 형식 오류를 400으로 갈라 주면
 * "호출자를 못 정했다"는 같은 사실이 두 응답으로 샌다.
 */
class HeaderCallerIdentityResolver implements CallerIdentityResolver {
    static final String CALLER_HEADER = "X-Caller-Person-Id";

    @Override
    public long resolve(NativeWebRequest request) {
        String header = request.getHeader(CALLER_HEADER);

        if (header == null || header.isBlank()) {
            throw new UnauthenticatedException("호출자 식별 정보가 없습니다");
        }

        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            throw new UnauthenticatedException("호출자 식별 정보가 올바르지 않습니다");
        }
    }
}
