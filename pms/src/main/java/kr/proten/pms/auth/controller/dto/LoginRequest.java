package kr.proten.pms.auth.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 로그인 요청 — email이 로그인 ID다 (PRD-pms §3). */
public record LoginRequest(
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "이메일 형식이 아닙니다") String email,
        @NotBlank(message = "비밀번호는 필수입니다") String password) {
}
