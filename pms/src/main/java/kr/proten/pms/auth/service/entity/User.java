package kr.proten.pms.auth.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 로그인 계정 (PRD-pms §4) — Person과 1:1이고 로그인 ID는 email이다 (§3).
 * 비밀번호는 해시만 보관한다 — 평문은 어디에도 남기지 않는다.
 */
@Entity
@Table(name = "users")
public class User {
    @Id
    private Long id;
    @Column(name = "person_id", nullable = false)
    private Long personId;
    // 로그인 ID
    @Column(nullable = false)
    private String email;
    // BCrypt 해시
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column
    private String phone;
    @Version
    private long version;

    protected User() {
    }

    private User(Long id, Long personId, String email, String passwordHash, String phone) {
        this.id = id;
        this.personId = personId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
    }

    /**
     * 로그인 계정을 만든다 (AC E2-1) — 식별자를 받는 이유는 Person과 같다(시드 정본 보존).
     * 평문 비밀번호는 인자에 없다: 해시만 받아 저장한다.
     */
    public static User of(
            Long id,
            Long personId,
            String email,
            String passwordHash,
            String phone) {
        return new User(id, personId, email, passwordHash, phone);
    }

    /**
     * 연락처를 바꾼다 (AC H1-2) — email은 로그인 ID다.
     * 비밀번호는 여기서 바뀌지 않는다: 현재 비밀번호 확인이 전제인 다른 행위다(H1-3).
     */
    public void updateContact(String email, String phone) {
        this.email = email;
        this.phone = phone;
    }

    /**
     * 비밀번호를 바꾼다 (AC H1-3) — <b>해시만 받는다</b>.
     * 평문이 엔티티에 들어오면 그것을 로그·toString으로 흘릴 자리가 생긴다.
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public Long getId() {
        return id;
    }

    public Long getPersonId() {
        return personId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public long getVersion() {
        return version;
    }

    /** 비밀번호 해시는 담지 않는다 — 로그·예외 메시지로 새는 경로를 막는다. */
    @Override
    public String toString() {
        return "User{id=" + id + ", personId=" + personId + ", email=" + email + "}";
    }
}
