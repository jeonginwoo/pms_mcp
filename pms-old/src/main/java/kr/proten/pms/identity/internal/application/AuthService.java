package kr.proten.pms.identity.internal.application;

import kr.proten.pms.identity.internal.domain.Person;
import kr.proten.pms.identity.internal.domain.User;
import kr.proten.pms.identity.internal.domain.repository.PersonRepository;
import kr.proten.pms.identity.internal.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자체 로그인 유스케이스 (§7 인증 — email+비밀번호 → JWT).
 * 실패 사유(미존재·불일치·비활성)는 전부 같은 예외로 수렴한다 — 계정 존재 탐지 방지.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {
    // 로그인 계정 조회
    private final UserRepository userRepository;
    // 계정 활성 상태(E2-3 soft 삭제) 확인
    private final PersonRepository personRepository;
    // 비밀번호 검증 (BCrypt — infra)
    private final PasswordHasher passwordHasher;
    // JWT 발급·refresh 검증 (RS256 — infra)
    private final TokenProvider tokenProvider;

    public AuthService(
            UserRepository userRepository,
            PersonRepository personRepository,
            PasswordHasher passwordHasher,
            TokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.personRepository = personRepository;
        this.passwordHasher = passwordHasher;
        this.tokenProvider = tokenProvider;
    }

    /**
     * email+비밀번호를 검증하고 access(1h)+refresh(14d) 쌍을 발급합니다.
     */
    public IssuedTokens login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        Person person = personRepository.findById(user.personId())
                .orElseThrow(InvalidCredentialsException::new);

        if (!person.active()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        return tokenProvider.issue(person.id());
    }

    /**
     * refresh 토큰을 검증하고 새 토큰 쌍으로 회전합니다 (§7 JWT 정책).
     */
    public IssuedTokens refresh(String refreshToken) {
        Long personId = tokenProvider.verifyRefresh(refreshToken);
        Person person = personRepository.findById(personId)
                .orElseThrow(InvalidTokenException::new);

        if (!person.active()) {
            throw new InvalidTokenException();
        }

        return tokenProvider.issue(person.id());
    }
}
