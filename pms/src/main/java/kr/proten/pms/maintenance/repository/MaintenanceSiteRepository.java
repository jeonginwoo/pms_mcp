package kr.proten.pms.maintenance.repository;

import java.util.Collection;
import java.util.List;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 유지보수 사이트 저장소 — 계약 검색의 세 번째 축(사이트명)이 여기서 나온다. */
public interface MaintenanceSiteRepository extends JpaRepository<MaintenanceSite, Long> {

    List<MaintenanceSite> findByContractIdOrderByNameAsc(Long contractId);

    List<MaintenanceSite> findByContractIdInOrderByNameAsc(Collection<Long> contractIds);

    /**
     * 사이트명으로 계약 id를 찾는다 (AC D4-1의 세 번째 매칭 축).
     *
     * <p>이 축이 없으면 45사이트 계약에 도달할 수 없다: 사용자는 "가천대길병원"으로
     * 부르는데 그 문자열은 계약명("그룹웨어 유지보수")·계약사("㈜가온아이")에 없고
     * 사이트명에만 있다(2026-08-11 결정 근거).
     */
    @Query("""
            select distinct s.contractId from MaintenanceSite s
            where lower(s.name) like lower(concat('%', :keyword, '%'))
            """)
    List<Long> findContractIdsByNameContaining(@Param("keyword") String keyword);

    /** 매칭된 사이트를 응답에 동봉하기 위한 조회 — 무엇 때문에 걸렸는지 보여 준다. */
    @Query("""
            select s from MaintenanceSite s
            where s.contractId in :contractIds
              and lower(s.name) like lower(concat('%', :keyword, '%'))
            order by s.name asc
            """)
    List<MaintenanceSite> findMatching(
            @Param("contractIds") Collection<Long> contractIds, @Param("keyword") String keyword);
}
