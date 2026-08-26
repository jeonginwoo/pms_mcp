package kr.proten.pms.maintenance.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
     *
     * <p><b>삭제분은 빠진다</b>(2026-08-26 — AC D3-6 soft 삭제). 조건을 질의가 들고
     * 있는 이유는 호출자가 뒤집을 수 없어야 하기 때문이다: 파라미터로 열어 두면
     * "삭제된 것도 보기"가 어디선가 켜지고, 그 화면은 AC에 없다.
     */
    @Query("""
            select i from MaintenanceIssue i
            where i.deleted = false
              and (cast(:statusName as string) is null or cast(i.status as string) = :statusName)
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
            where i.deleted = false
              and i.siteId in :siteIds
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
            where i.deleted = false
              and i.siteId in :siteIds
            group by i.status
            """)
    List<Object[]> countByStatus(@Param("siteIds") Collection<Long> siteIds);

    /**
     * 살아 있는 이슈 하나 — 상세·쓰기가 전부 이것으로 읽는다 (AC D3-6).
     *
     * <p>{@code findById}를 쓰면 삭제된 이슈가 상세로 열리고 수정까지 된다. 삭제와
     * 부재는 같은 404다 — 무엇이 있었는지 알려 줄 이유가 없다.
     */
    @Query("select i from MaintenanceIssue i where i.id = :issueId and i.deleted = false")
    Optional<MaintenanceIssue> findActiveById(@Param("issueId") long issueId);

    /**
     * 다음 id — 시드가 원본 이슈 번호(230~496)를 쓰므로 새 이슈는 그 위에서 시작한다.
     * 계약 id 공간(1~105)과 겹치지 않는 것이 도구의 id 해석 전제다(2026-08-23 결정).
     */
    @Query("select coalesce(max(i.id), 0) + 1 from MaintenanceIssue i")
    long nextId();
}
