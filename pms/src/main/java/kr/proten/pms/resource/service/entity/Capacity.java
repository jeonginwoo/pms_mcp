package kr.proten.pms.resource.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.YearMonth;

/**
 * 월별 가용 M/M (PRD-pms §4 resource).
 *
 * `Person.capacity`가 이미 있는데 이 엔티티가 따로 있는 이유: Person 쪽은 **기본값**
 * (부록 B — 1.0)이고 여기는 특정 월의 예외다(휴직·파견처럼 그 달만 다른 경우).
 * 행이 없으면 Person의 기본값을 쓴다 — 44명 × 12개월을 미리 채우지 않는다.
 *
 * 가동률 자체는 저장하지 않는다: 배정 합산으로 매 조회 계산한다(캐시 미도입 —
 * 2026-08-06 결정). 저장하면 배정이 바뀔 때마다 두 원본이 어긋난다.
 */
@Entity
@Table(name = "capacities")
public class Capacity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "person_id", nullable = false)
    private Long personId;
    // "YYYY-MM" — 월 단위 비교·정렬이 문자열로 성립하는 형식
    @Column(name = "year_month_value", nullable = false, length = 7)
    private String yearMonth;
    @Column(name = "available_mm", nullable = false)
    private double availableMm;
    @Version
    private long version;

    protected Capacity() {
    }

    private Capacity(Long personId, YearMonth month, double availableMm) {
        this.personId = personId;
        this.yearMonth = month.toString();
        this.availableMm = availableMm;
    }

    /** 그 달의 가용 M/M을 정한다 — 음수는 가동률의 분모가 될 수 없다. */
    public static Capacity of(Long personId, YearMonth month, double availableMm) {
        if (personId == null || month == null) {
            throw new IllegalArgumentException("personId·month는 필수입니다");
        }

        if (availableMm <= 0) {
            throw new IllegalArgumentException("가용 M/M은 0보다 커야 합니다: " + availableMm);
        }

        return new Capacity(personId, month, availableMm);
    }

    public Long getId() {
        return id;
    }

    public Long getPersonId() {
        return personId;
    }

    public YearMonth getMonth() {
        return YearMonth.parse(yearMonth);
    }

    public double getAvailableMm() {
        return availableMm;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Capacity{personId=" + personId + ", month=" + yearMonth
                + ", availableMm=" + availableMm + "}";
    }
}
