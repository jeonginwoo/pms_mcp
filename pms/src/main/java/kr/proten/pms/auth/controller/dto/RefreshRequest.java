package kr.proten.pms.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;

/** 갱신 요청 — refresh 토큰은 본문으로 받는다(URL·로그에 남지 않게). */
public record RefreshRequest(@NotBlank(message = "refreshToken은 필수입니다") String refreshToken) {
}
