package kr.proten.pms.identity.internal.domain;

/**
 * 로그인 계정 — email이 로그인 ID (EPIC H). Person과 1:1.
 */
public record User(
        Long id,
        Long personId,
        String email,
        String passwordHash,
        String phone,
        NotifPrefs notifPrefs,
        long version) {
}
