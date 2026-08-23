package kr.proten.pms.mcp.internal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 호출자 식별 — JWT sub 클레임 기반 (구조 원칙 4: 사용자 토큰 패스스루).
 * 판정에 신뢰하는 클레임은 sub뿐 — 그룹·역할은 서버 데이터에서 조회한다(구현_노트 §1-2).
 *
 * common의 `CallerIdentityResolver`를 쓰지 않는 이유: 그쪽은 웹 요청용이고
 * `pms.auth.enabled`가 꺼지면 헤더를 신뢰하는 구현으로 바뀐다. `/mcp`는 스위치와
 * 무관하게 언제나 토큰만 신뢰해야 하므로(원칙 4) 자기 경로에서 직접 읽는다.
 */
@Component
class CallerContext {

    long callerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwt) {
            String subject = jwt.getToken().getSubject();
            try {
                return Long.parseLong(subject);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("토큰 sub가 사용자 id 형식이 아닙니다: " + subject);
            }
        }

        // 보안 체인이 /mcp 전체를 막고 있으므로 정상 경로에선 도달 불가 —
        // 도구가 요청 스레드 밖에서 실행되면(SecurityContext 미전파) 여기로 온다
        throw new IllegalStateException("인증된 호출자가 없습니다 — /mcp 보안 체인 밖 실행");
    }
}
