package kr.proten.pms.identity.internal.infra.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.proten.pms.identity.internal.domain.NotifPrefs;
import kr.proten.pms.identity.internal.domain.User;

/**
 * User 영속 매핑 — notifPrefs는 boolean 4컬럼으로 평탄화한다.
 */
@Entity
@Table(name = "users", schema = "identity")
class UserJpa {
    // 식별자
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Person 1:1 참조
    @Column(nullable = false, unique = true)
    private Long personId;
    // 로그인 ID
    @Column(nullable = false, unique = true)
    private String email;
    // BCrypt 해시
    @Column(nullable = false)
    private String passwordHash;
    // 연락처 (선택)
    private String phone;
    // 알림 설정 — 진척률
    @Column(nullable = false)
    private boolean notifProgress;
    // 알림 설정 — 프로젝트
    @Column(nullable = false)
    private boolean notifProject;
    // 알림 설정 — 조직
    @Column(nullable = false)
    private boolean notifOrg;
    // 알림 설정 — 주간
    @Column(nullable = false)
    private boolean notifWeekly;
    // 낙관적 락
    @Version
    private long version;

    protected UserJpa() {
    }

    static UserJpa fromDomain(User domain) {
        UserJpa entity = new UserJpa();
        entity.id = domain.id();
        entity.personId = domain.personId();
        entity.email = domain.email();
        entity.passwordHash = domain.passwordHash();
        entity.phone = domain.phone();
        entity.notifProgress = domain.notifPrefs().progress();
        entity.notifProject = domain.notifPrefs().project();
        entity.notifOrg = domain.notifPrefs().org();
        entity.notifWeekly = domain.notifPrefs().weekly();
        entity.version = domain.version();

        return entity;
    }

    User toDomain() {
        return new User(
                id,
                personId,
                email,
                passwordHash,
                phone,
                new NotifPrefs(notifProgress, notifProject, notifOrg, notifWeekly),
                version);
    }
}
