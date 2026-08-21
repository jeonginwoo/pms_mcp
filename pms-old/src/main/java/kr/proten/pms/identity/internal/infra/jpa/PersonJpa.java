package kr.proten.pms.identity.internal.infra.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.proten.pms.identity.internal.domain.Person;

/**
 * Person 영속 매핑.
 */
@Entity
@Table(name = "people", schema = "identity")
class PersonJpa {
    // 식별자
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 이름
    @Column(nullable = false)
    private String name;
    // 소속 조직 노드
    @Column(nullable = false)
    private Long orgUnitId;
    // 직급
    @Column(nullable = false)
    private Long gradeId;
    // 권한 그룹
    @Column(nullable = false)
    private Long groupId;
    // 월 가용 M/M 기본값
    @Column(nullable = false)
    private double capacity;
    // 가동률 집계 모집단 여부
    @Column(nullable = false)
    private boolean billable;
    // 시스템 계정 플래그
    @Column(nullable = false)
    private boolean system;
    // soft 삭제 상태 (false = 로그인 차단·목록 제외)
    @Column(nullable = false)
    private boolean active;
    // 낙관적 락
    @Version
    private long version;

    protected PersonJpa() {
    }

    static PersonJpa fromDomain(Person domain) {
        PersonJpa entity = new PersonJpa();
        entity.id = domain.id();
        entity.name = domain.name();
        entity.orgUnitId = domain.orgUnitId();
        entity.gradeId = domain.gradeId();
        entity.groupId = domain.groupId();
        entity.capacity = domain.capacity();
        entity.billable = domain.billable();
        entity.system = domain.system();
        entity.active = domain.active();
        entity.version = domain.version();

        return entity;
    }

    Person toDomain() {
        return new Person(
                id,
                name,
                orgUnitId,
                gradeId,
                groupId,
                capacity,
                billable,
                system,
                active,
                version);
    }
}
