package kr.proten.pms.person.service.impl;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt 구현 — 시드 해시(`gradle printPasswordHash`)와 같은 인코더다.
 */
@Component
class BCryptPasswordHasher implements PasswordHasher {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String storedHash) {
        return encoder.matches(rawPassword, storedHash);
    }
}
