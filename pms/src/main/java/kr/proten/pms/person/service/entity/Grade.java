package kr.proten.pms.person.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 직급 — 이름과 보정 가동률 계수(coeff). 시드 값은 PRD-pms 부록 B.
 */
@Entity
@Table(name = "grades")
public class Grade {
    @Id
    private Long id;
    @Column(nullable = false)
    private String name;
    // 보정 가동률 단가 가중치 (상위 PRD §3 — 예: 책임 1.2)
    @Column(nullable = false)
    private double coeff;
    @Version
    private long version;

    protected Grade() {
    }

    private Grade(Long id, String name, double coeff) {
        this.id = id;
        this.name = name;
        this.coeff = coeff;
    }

    /** 직급을 만든다 — 식별자를 받는 이유는 OrgUnit.of와 같다(시드 정본 보존). */
    public static Grade of(Long id, String name, double coeff) {
        return new Grade(id, name, coeff);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getCoeff() {
        return coeff;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Grade{id=" + id + ", name=" + name + ", coeff=" + coeff + "}";
    }
}
