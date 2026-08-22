package kr.proten.pms.auth.service;

import java.util.Map;
import kr.proten.pms.auth.service.dto.IssuedTokens;

/**
 * 자체 로그인 (PRD-pms §7 — email + 비밀번호 → JWT).
 *
 * 구현은 있지만 **보호 체인은 `pms.auth.enabled`가 켜질 때만 동작한다**
 * (2026-08-21 결정 — 만들어 두고 나중에 쓴다). 스위치가 꺼진 동안에도 로그인·갱신
 * 자체는 호출 가능해 토큰 발급을 미리 확인할 수 있다.
 */
public interface AuthService {

    /** email·비밀번호를 검증하고 access·refresh 쌍을 발급한다. */
    IssuedTokens login(String email, String rawPassword);

    /** refresh 토큰을 검증하고 새 쌍으로 회전한다. */
    IssuedTokens refresh(String refreshToken);

    /** 공개키 셋(JWKS) — 개인키는 포함되지 않는다. `/mcp` 어댑터 디코더의 소비 지점. */
    Map<String, Object> publicJwks();
}
