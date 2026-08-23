package kr.proten.pms.auth.service.impl;

/**
 * 비밀번호 해시 — 알고리즘을 유스케이스에서 떼어 놓는 지점.
 * 해시 방식이 바뀌어도 로그인·계정 생성 흐름은 그대로다.
 */
interface PasswordHasher {

    /** 저장할 해시를 만든다 (AC E2-1 계정 생성) — 평문은 어디에도 남기지 않는다. */
    String hash(String rawPassword);

    boolean matches(String rawPassword, String storedHash);
}
