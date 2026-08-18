package kr.proten.pms.identity.internal.web;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import kr.proten.pms.identity.internal.application.AuthService;
import kr.proten.pms.identity.internal.application.IssuedTokens;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자체 로그인 API (§7 — POST /api/auth/login·refresh).
 * JWKS 엔드포인트는 /mcp 체인 디코더(pms.auth.jwks-uri — 구현_노트 §1-1)의 소비 지점.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {
    // 로그인·갱신 유스케이스
    private final AuthService authService;
    // JWKS 공개용 서명 키
    private final RSAKey rsaKey;

    AuthController(AuthService authService, RSAKey rsaKey) {
        this.authService = authService;
        this.rsaKey = rsaKey;
    }

    /** 로그인 요청 — email이 로그인 ID (§3). */
    record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    /** 갱신 요청 — refresh 토큰은 본문으로 받는다. */
    record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** 토큰 응답 — access 1시간·refresh 14일 (§7 JWT 정책). */
    record TokenResponse(String accessToken, String refreshToken) {

        static TokenResponse from(IssuedTokens tokens) {
            return new TokenResponse(tokens.accessToken(), tokens.refreshToken());
        }
    }

    @PostMapping("/login")
    TokenResponse login(@RequestBody @Valid LoginRequest request) {
        return TokenResponse.from(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@RequestBody @Valid RefreshRequest request) {
        return TokenResponse.from(authService.refresh(request.refreshToken()));
    }

    /** 공개키 셋(JWKS) — 개인키는 포함되지 않는다. */
    @GetMapping("/jwks")
    Map<String, Object> jwks() {
        return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }
}
