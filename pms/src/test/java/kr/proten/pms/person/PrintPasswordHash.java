package kr.proten.pms.person;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 시드 users 행에 넣을 BCrypt 해시 출력 (gradle printPasswordHash).
 * 시드 SQL에 해시를 박아 두는 이유: 43행을 기동 시마다 해시하면 부팅이 느려지고,
 * 초기 비밀번호는 부록 B가 못 박은 고정값이라 재생성이 잦지 않다.
 */
public final class PrintPasswordHash {
    private PrintPasswordHash() {
    }

    public static void main(String[] args) {
        String raw = args.length > 0 ? args[0] : "proten1!";
        System.out.println(new BCryptPasswordEncoder().encode(raw));
    }
}
