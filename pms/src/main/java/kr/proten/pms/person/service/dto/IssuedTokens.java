package kr.proten.pms.person.service.dto;

/**
 * 발급된 토큰 쌍 (PRD-pms §7 JWT 정책 — access 1시간 · refresh 14일).
 */
public record IssuedTokens(String accessToken, String refreshToken) {
}
