package kr.proten.pms.identity.internal.infra.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.proten.pms.identity.internal.domain.Grade;

/**
 * Grade 영속 매핑.
 */
@Entity
@Table(name = "grades", schema = "identity")
class GradeJpa {
    // 식별자
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 직급명
    @Column(nullable = false)
    private String name;
    // 보정 가동률 계수
    @Column(nullable = false)
    private double coeff;
    // 낙관적 락
    @Version
    private long version;

    protected GradeJpa() {
    }

    static GradeJpa fromDomain(Grade domain) {
        GradeJpa entity = new GradeJpa();
        entity.id = domain.id();
        entity.name = domain.name();
        entity.coeff = domain.coeff();
        entity.version = domain.version();

        return entity;
    }

    Grade toDomain() {
        return new Grade(id, name, coeff, version);
    }
}
