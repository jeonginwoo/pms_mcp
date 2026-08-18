package kr.proten.pms.identity.internal.application;

/**
 * 로그인·갱신 결과로 내려주는 토큰 쌍 (§7 JWT 정책 — access 1시간·refresh 14일).
 */
public record IssuedTokens(String accessToken, String refreshToken) {
}
