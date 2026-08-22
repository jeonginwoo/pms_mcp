package kr.proten.pms.auth.service.impl;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import kr.proten.pms.auth.repository.UserRepository;
import kr.proten.pms.auth.service.AuthService;
import kr.proten.pms.auth.service.dto.IssuedTokens;
import kr.proten.pms.auth.service.entity.User;
import kr.proten.pms.auth.service.impl.token.TokenProvider;
import kr.proten.pms.common.exception.UnauthenticatedException;
import kr.proten.pms.person.PersonDirectoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자체 로그인 유스케이스 (PRD-pms §7).
 *
 * 실패 사유(계정 없음·비밀번호 불일치·비활성 인원)는 **전부 같은 예외·같은 문구로
 * 수렴시킨다** — 사유를 갈라 주면 어떤 email이 존재하는지 탐지할 수 있다.
 *
 * 인원의 활성 여부는 person이 공개한 계약(`PersonDirectoryService`)에 묻는다 —
 * auth는 person의 저장소나 엔티티를 보지 않는다(2026-08-22 모듈 분리).
 */
@Service
@Transactional(readOnly = true)
class AuthServiceImpl implements AuthService {
    // 로그인 실패 정본 문구 — 사유별로 갈라지지 않는다
    private static final String LOGIN_FAILED = "이메일 또는 비밀번호가 올바르지 않습니다";

    private final UserRepository userRepository;
    private final PersonDirectoryService personDirectoryService;
    private final PasswordHasher passwordHasher;
    private final TokenProvider tokenProvider;
    // JWKS 공개용 서명 키
    private final RSAKey rsaKey;

    AuthServiceImpl(
            UserRepository userRepository,
            PersonDirectoryService personDirectoryService,
            PasswordHasher passwordHasher,
            TokenProvider tokenProvider,
            RSAKey rsaKey) {
        this.userRepository = userRepository;
        this.personDirectoryService = personDirectoryService;
        this.passwordHasher = passwordHasher;
        this.tokenProvider = tokenProvider;
        this.rsaKey = rsaKey;
    }

    @Override
    public IssuedTokens login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthenticatedException(LOGIN_FAILED));

        if (!personDirectoryService.existsActive(user.getPersonId())) {
            throw new UnauthenticatedException(LOGIN_FAILED);
        }

        if (!passwordHasher.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthenticatedException(LOGIN_FAILED);
        }

        return tokenProvider.issue(user.getPersonId());
    }

    @Override
    public IssuedTokens refresh(String refreshToken) {
        Long personId = tokenProvider.verifyRefresh(refreshToken);

        if (!personDirectoryService.existsActive(personId)) {
            throw new UnauthenticatedException("토큰을 사용할 수 없습니다");
        }

        return tokenProvider.issue(personId);
    }

    @Override
    public Map<String, Object> publicJwks() {
        return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }
}
