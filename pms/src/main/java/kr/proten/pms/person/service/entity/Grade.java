package kr.proten.pms.person.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.proten.pms.common.exception.StaleVersionException;

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

    /**
     * 이름·계수를 함께 바꾼다 (AC E4-2).
     *
     * <p>둘을 한 메서드로 묶는 이유: 수정 화면이 한 폼이고 §7도 하나의 PUT이다.
     * 계수만 바꾸는 별도 경로를 두면 "이름은 그대로"를 호출부가 매번 표현해야 한다.
     *
     * <p><b>보정 가동률은 다음 조회부터 즉시 바뀐다</b> — 캐시가 없어 매 조회 계산이다
     * (2026-08-06 결정). 여기서 재계산을 부르지 않는 것이 설계다.
     */
    public void update(String name, double coeff) {
        this.name = name;
        this.coeff = coeff;
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

    /**
     * 낙관적 락 검사 (AC E4-2) — 최신 version을 알려 재조회 후 재시도하게 한다.
     * 관리 화면은 여러 관리자가 같은 행을 열어 두는 자리라 마지막 쓰기가 조용히
     * 이기면 앞사람의 변경이 흔적 없이 사라진다 (§7 동시성 규약).
     */
    public void requireVersion(long expected) {
        if (version != expected) {
            throw new StaleVersionException("최신 직급 version " + version);
        }
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Grade{id=" + id + ", name=" + name + ", coeff=" + coeff + "}";
    }
}
