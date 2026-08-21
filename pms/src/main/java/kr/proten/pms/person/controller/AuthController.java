package kr.proten.pms.person.controller;

import jakarta.validation.Valid;
import java.util.Map;
import kr.proten.pms.person.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자체 로그인 API (PRD-pms §7).
 *
 * 이 라우트는 `pms.auth.enabled`와 무관하게 열려 있다 — 스위치를 켜기 전에도
 * 토큰 발급을 확인할 수 있어야 하고, 켠 뒤에는 보호 체인이 이 세 경로만 허용한다.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(authService.refresh(request.refreshToken()));
    }

    /** 공개키 셋 — 개인키는 포함되지 않는다. `/mcp` 어댑터 디코더의 소비 지점. */
    @GetMapping("/jwks")
    Map<String, Object> jwks() {
        return authService.publicJwks();
    }
}
