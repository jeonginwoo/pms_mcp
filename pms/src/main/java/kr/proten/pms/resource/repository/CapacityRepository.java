package kr.proten.pms.resource.repository;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.resource.service.entity.Capacity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 월별 가용 M/M 저장소 — 행이 없으면 Person의 기본 capacity를 쓴다. */
public interface CapacityRepository extends JpaRepository<Capacity, Long> {

    Optional<Capacity> findByPersonIdAndYearMonth(Long personId, String yearMonth);

    /** 집계용 — 그 달에 예외가 걸린 인원만 돌아온다(전원이 아니다). */
    List<Capacity> findByYearMonth(String yearMonth);
}
