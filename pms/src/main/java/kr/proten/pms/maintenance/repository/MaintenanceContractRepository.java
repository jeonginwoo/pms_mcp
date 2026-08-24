package kr.proten.pms.maintenance.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.proten.pms.maintenance.service.entity.MaintenanceContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 유지보수 계약 저장소.
 *
 * <p>목록 질의는 <b>계약명·계약사만</b> 본다. 사이트명 매칭(D4-1의 세 번째 축)은
 * 사이트 저장소가 따로 답한 id 집합으로 합친다 — 한 질의에 join을 섞으면 45사이트
 * 계약이 45행으로 불어나고 페이징 총건수가 틀어진다.
 *
 * <p><b>선택 필터를 문자열 패턴으로 받는다</b>: {@code :param is null} 형태를
 * PostgreSQL에 그대로 보내면 타입 없는 null이 되어 연산자를 찾지 못한다
 * ("No function matches the given name and argument types"). 그래서 like 패턴은
 * 서비스가 Java에서 만들고, null 판정은 {@code cast(... as string)}으로 타입을 준다.
 */
public interface MaintenanceContractRepository extends JpaRepository<MaintenanceContract, Long> {


    /**
     * 이관으로 생긴 계약 (AC D1-1) — 프로젝트:계약 1:1이다(2026-08-06 결정).
     *
     * <p>직접 등록 계약은 {@code sourceProjectId}가 null이라 이 질의에 걸리지 않는다.
     * 프로젝트 화면이 "이 프로젝트의 유지보수 계약"으로 가는 경로이자, 이관이 정말
     * 일어났는지를 확인하는 지점이다.
     */
    Optional<MaintenanceContract> findBySourceProjectId(Long sourceProjectId);

    /**
     * 계약 목록 (AC D4-1). 정렬은 종료일 내림차순 고정 —
     * {@code search_maintenance}의 도구 description이 약속한 순서다.
     *
     * @param statusName 상태 enum 이름. null이면 상태로 거르지 않는다
     * @param contractorPattern {@code %가온%} 형태의 소문자 패턴 또는 null
     * @param keywordPattern 계약명·계약사에 걸 소문자 패턴 또는 null
     * @param contractIdsBySite 사이트명으로 먼저 찾아낸 계약 id (빈 집합을 넘기지 않는다)
     */
    @Query("""
            select c from MaintenanceContract c
            where (cast(:statusName as string) is null or cast(c.status as string) = :statusName)
              and (cast(:contractorPattern as string) is null
                   or lower(c.contractor) like :contractorPattern)
              and (cast(:endedBefore as date) is null or c.endDate <= :endedBefore)
              and (cast(:keywordPattern as string) is null
                   or lower(c.name) like :keywordPattern
                   or lower(c.contractor) like :keywordPattern
                   or c.id in :contractIdsBySite)
            order by c.endDate desc nulls last, c.id desc
            """)
    Page<MaintenanceContract> search(
            @Param("statusName") String statusName,
            @Param("contractorPattern") String contractorPattern,
            @Param("endedBefore") LocalDate endedBefore,
            @Param("keywordPattern") String keywordPattern,
            @Param("contractIdsBySite") Collection<Long> contractIdsBySite,
            Pageable pageable);

    /** 상세 조립 시 여러 계약을 한 번에 — 이슈 목록이 계약명을 붙일 때 쓴다. */
    List<MaintenanceContract> findByIdIn(Collection<Long> ids);

    /**
     * 다음 id — 계약·이슈는 시드가 <b>원본 번호</b>를 명시 지정하므로(계약 1~105,
     * 이슈 230~496) identity 생성을 쓰지 않는다. 두 id 공간이 겹치지 않는 것이
     * {@code list_maintenance_logs}의 "계약 id 또는 이슈 id" 해석을 가능하게 한다
     * (2026-08-23 결정). 하드 삭제가 없으므로(D2-2 — 계약 종료는 상태로) max+1로 충분하다.
     */
    @Query("select coalesce(max(c.id), 0) + 1 from MaintenanceContract c")
    long nextId();
}
