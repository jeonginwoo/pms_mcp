package kr.proten.pms.common.config;

import java.security.Principal;
import kr.proten.pms.common.exception.UnauthenticatedException;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * 인증이 켜진 뒤의 호출자 식별 — 토큰 subject(=personId).
 *
 * 보호 체인이 이미 인증을 보장한 뒤에만 도달하므로, principal 부재나 비숫자 subject는
 * 요청 오류가 아니라 구성 결함이다 — 그래도 401로 닫는다(열어 두는 쪽이 위험하다).
 */
class TokenCallerIdentityResolver implements CallerIdentityResolver {
    @Override
    public long resolve(NativeWebRequest request) {
        Principal principal = request.getUserPrincipal();

        if (principal == null) {
            throw new UnauthenticatedException("인증이 필요합니다");
        }

        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            throw new UnauthenticatedException("토큰을 사용할 수 없습니다");
        }
    }
}
