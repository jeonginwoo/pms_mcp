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

    /**
     * 내 비밀번호를 바꾼다 (AC H1-3).
     *
     * <p><b>이 행위는 모듈 경계를 넘지 않는다</b>: 현재 비밀번호 확인도 새 해시
     * 저장도 auth 안의 일이라 person이 알 이유가 없다. 그래서 `AccountPort`가
     * 아니라 auth의 컨트롤러가 `/api/me/password`를 직접 연다 — `/api/me/*`는
     * <b>데이터를 가진 모듈</b>이 갖는다(`notif-prefs`가 notification에 있는 선례).
     *
     * <p>현재 비밀번호 불일치와 새 비밀번호 형식 오류를 <b>같은 400</b>으로
     * 수렴시킨다(AC H1-3 문면). 갈라 주면 "현재 비밀번호는 맞았다"가 응답으로
     * 새어 나가 대입 공격에 힌트가 된다.
     */
    void changePassword(long callerPersonId, String currentPassword, String newPassword);

    /** 공개키 셋(JWKS) — 개인키는 포함되지 않는다. `/mcp` 어댑터 디코더의 소비 지점. */
    /** 공개키 셋(JWKS) — 개인키는 포함되지 않는다. `/mcp` 어댑터 디코더의 소비 지점. */
    Map<String, Object> publicJwks();
}
