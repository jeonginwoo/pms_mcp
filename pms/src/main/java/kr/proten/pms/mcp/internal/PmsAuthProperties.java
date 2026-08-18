package kr.proten.pms.mcp.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * /mcp 토큰 검증 설정 (구현_노트 §1-1·B-3).
 * jwksUri가 설정되면 JWKS 디코더(실전), 없으면 hs256Secret으로 로컬 테스트
 * 디코더를 쓴다 — 실제 발급 체계(로그인/BFF 위임 토큰)는 PMS-M1 이후라
 * M0 게이트(인증 3케이스)는 HS256 테스트 JWT로 검증한다(2026-08-18 승인).
 */
@ConfigurationProperties(prefix = "pms.auth")
public record PmsAuthProperties(String jwksUri, String hs256Secret) {
}
