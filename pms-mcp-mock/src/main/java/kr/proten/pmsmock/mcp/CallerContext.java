package kr.proten.pmsmock.mcp;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 호출자 식별 — B2-2부터 JWT sub 클레임 기반 (구조 원칙 4: 사용자 토큰 패스스루).
 * B2-0의 프로퍼티 고정(mock.caller-id)을 대체 — 도구·서비스 시그니처는 그대로.
 */
@Component
public class CallerContext {

    public int callerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            String sub = jwt.getToken().getSubject();
            try {
                return Integer.parseInt(sub);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("토큰 sub가 사용자 id 형식이 아닙니다: " + sub);
            }
        }
        // 보안 체인이 /mcp 전체를 막고 있으므로 정상 경로에선 도달 불가 —
        // 도구가 요청 스레드 밖에서 실행되면(SecurityContext 미전파) 여기로 온다
        throw new IllegalStateException("인증된 호출자가 없습니다 — /mcp 보안 체인 밖 실행");
    }
}
