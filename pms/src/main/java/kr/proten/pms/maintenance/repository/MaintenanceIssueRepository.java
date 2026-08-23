package kr.proten.pms.maintenance.repository;

import java.util.Collection;
import java.util.List;
import kr.proten.pms.maintenance.service.entity.MaintenanceIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 유지보수 이슈 저장소. */
public interface MaintenanceIssueRepository extends JpaRepository<MaintenanceIssue, Long> {

    /**
     * 이슈 목록 (AC D3-4). 필터는 전부 선택이다.
     *
     * <p>{@code unassignedOnly}가 별도 파라미터인 이유: "미배정만"은 {@code assigneeId}에
     * null을 넘겨 표현할 수 없다 — 그 null은 이미 "담당자로 거르지 않는다"를 뜻한다.
     * 값이 없다는 것과 조건이 없다는 것은 다른 말이다.
     *
     * <p>enum·id 선택 필터는 {@code cast(... as ...)}로 타입을 준다: 타입 없는 null을
     * PostgreSQL에 보내면 연산자를 찾지 못한다.
     *
     * <p>정렬은 접수일 내림차순 고정 — 이슈 목록은 최근 들어온 것이 먼저다.
     */
    @Query("""
            select i from MaintenanceIssue i
            where (cast(:statusName as string) is null or cast(i.status as string) = :statusName)
              and (cast(:typeName as string) is null or cast(i.type as string) = :typeName)
              and (cast(:siteId as long) is null or i.siteId = :siteId)
              and (cast(:assigneeId as long) is null or i.assigneeId = :assigneeId)
              and (:unassignedOnly = false or i.assigneeId is null)
              and (:allSites = true or i.siteId in :siteIds)
            order by i.receivedAt desc, i.id desc
            """)
    Page<MaintenanceIssue> search(
            @Param("statusName") String statusName,
            @Param("typeName") String typeName,
            @Param("siteId") Long siteId,
            @Param("assigneeId") Long assigneeId,
            @Param("unassignedOnly") boolean unassignedOnly,
            @Param("allSites") boolean allSites,
            @Param("siteIds") Collection<Long> siteIds,
            Pageable pageable);

    /**
     * 계약에 속한 이슈 (MCP {@code list_maintenance_logs}의 계약 id 경로).
     * 최근 50건 절단은 도구 description이 약속한 것이라 호출자가 {@code Pageable}로 준다.
     */
    @Query("""
            select i from MaintenanceIssue i
            where i.siteId in :siteIds
              and (cast(:typeName as string) is null or cast(i.type as string) = :typeName)
            order by i.receivedAt desc, i.id desc
            """)
    List<MaintenanceIssue> findBySiteIds(
            @Param("siteIds") Collection<Long> siteIds,
            @Param("typeName") String typeName,
            Pageable pageable);

    /** 계약 상세(D4-2)의 이슈 요약 — 상태별 건수를 행을 싣지 않고 센다. */
    @Query("""
            select i.status, count(i) from MaintenanceIssue i
            where i.siteId in :siteIds
            group by i.status
            """)
    List<Object[]> countByStatus(@Param("siteIds") Collection<Long> siteIds);
}
