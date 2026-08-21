package kr.proten.pms.person.controller;

import kr.proten.pms.person.service.dto.IssuedTokens;

/** 토큰 응답 — access 1시간 · refresh 14일 (PRD-pms §7 JWT 정책). */
public record TokenResponse(String accessToken, String refreshToken) {

    static TokenResponse from(IssuedTokens tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
