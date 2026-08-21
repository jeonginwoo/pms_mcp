package kr.proten.pms.identity.internal.application;

/**
 * 비밀번호 해시 포트 — 구현은 infra(BCrypt).
 */
public interface PasswordHasher {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
