package kr.proten.pms.auth.service.impl;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import kr.proten.pms.common.exception.ValidationException;
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
    /** AC H1-3 — 새 비밀번호 최소 길이. */
    private static final int MIN_PASSWORD_LENGTH = 8;

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

    /**
     * 비밀번호 변경 (AC H1-3) — 현재 비밀번호 확인 · 8자 이상 · 해시 저장.
     *
     * <p><b>세 실패가 같은 400으로 수렴한다</b>: 계정 없음·현재 비밀번호 불일치·
     * 새 비밀번호 형식 오류. 갈라 주면 "현재 비밀번호는 맞았다"거나 "그 계정은 있다"가
     * 응답으로 새어 나간다 — 로그인이 email 존재 여부를 숨기는 것과 같은 이유다.
     *
     * <p><b>수렴은 상태 코드가 아니라 문구·필드까지다</b>(2026-08-25 리뷰가 잡았다):
     * 처음엔 셋이 다른 message·field를 냈는데, 그러면 세션을 탈취한 공격자가
     * {@code changePassword(추측값, "short")}를 반복해 <b>비밀번호를 바꾸지 않고도</b>
     * 현재 비밀번호를 맞혔는지 알 수 있다(정답이면 길이 오류, 오답이면 불일치 오류).
     * 검사 순서가 current → length라 정확히 오라클이 됐다.
     *
     * <p>평문은 어디에도 남기지 않는다: 해시만 엔티티로 들어가고({@code User.changePassword})
     * 예외 문구에도 입력값이 들어가지 않는다.
     */
    @Override
    @Transactional
    public void changePassword(long callerPersonId, String currentPassword, String newPassword) {
        User user = userRepository.findByPersonId(callerPersonId)
                .orElseThrow(AuthServiceImpl::sameFailure);

        if (currentPassword == null
                || !passwordHasher.matches(currentPassword, user.getPasswordHash())) {
            throw sameFailure();
        }

        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw sameFailure();
        }

        user.changePassword(passwordHasher.hash(newPassword));
    }

    /**
     * 세 실패가 나눠 갖는 <b>하나의</b> 400 (AC H1-3).
     *
     * <p>문구가 새 비밀번호 규칙을 함께 말하는 이유: 사용자는 무엇을 고쳐야 하는지
     * 알아야 하고, 그 안내는 어느 갈래에서 왔는지를 <b>드러내지 않는다</b>.
     * 필드도 하나로 고정한다 — 화면이 필드로 갈래를 되짚을 수 있으면 같은 누출이다.
     */
    private static ValidationException sameFailure() {
        return new ValidationException(
                "현재 비밀번호가 올바르지 않거나 새 비밀번호가 "
                        + MIN_PASSWORD_LENGTH + "자 미만입니다", "password");
    }
}
