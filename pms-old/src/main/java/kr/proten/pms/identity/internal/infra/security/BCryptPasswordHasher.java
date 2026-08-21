package kr.proten.pms.identity.internal.infra.security;

import kr.proten.pms.identity.internal.application.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * PasswordHasher의 BCrypt 구현.
 */
@Component
class BCryptPasswordHasher implements PasswordHasher {
    // BCrypt 인코더 (기본 강도 10)
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
