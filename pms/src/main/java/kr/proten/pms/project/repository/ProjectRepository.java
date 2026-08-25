package kr.proten.pms.project.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.service.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 프로젝트 저장소.
 * 가시성 필터는 파생 질의로 DB에 내려 보낸다 — 382건을 전부 올려 메모리에서
 * 거르는 방식은 금지다(conventions/java-spring.md §6).
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * 마감 임박 — 종료일이 {@code through} 이내인 진행중 프로젝트 (AC F2-1, D-7).
     *
     * <p>스케줄러의 질의라 <b>화자가 없다</b>: 가시성은 사람이 볼 때의 규칙이고,
     * 일일 점검은 회사 전체를 본 뒤 각 프로젝트의 PM에게만 알린다. 그래서
     * {@code ProjectLookupService}(가시성 판정 포함)를 쓸 수 없다.
     *
     * <p><b>하한이 오늘이다</b>(2026-08-25 정정): "임박"은 다가오는 것이고, 이미
     * 지난 종료일은 임박이 아니라 데이터 문제다. 하한이 없으면 <b>배포 첫날</b>
     * 시드의 진행중 34건 중 종료일이 지난 16건(2026-05-30까지 거슬러 간다)이
     * 한꺼번에 "마감 임박"으로 나간다 — V15가 F3에서 막아 둔 것과 같은 사고이고,
     * F2에만 그 방어가 없었다. 지난 마감을 다루는 AC는 없다.
     */
    @Query("""
            select p from Project p
            where p.deleted = false
              and p.status = kr.proten.pms.project.ProjectStatus.IN_PROGRESS
              and p.endDate is not null
              and p.endDate >= :from
              and p.endDate <= :through
            order by p.endDate asc
            """)
    List<Project> findDeadlineNear(
            @Param("from") LocalDate from, @Param("through") LocalDate through);

    /**
     * 완료 지연 — 100%에 도달한 지 {@code since} 이전인 진행중 프로젝트 (AC F3-1, 7일).
     *
     * <p>{@code hundredReachedAt}이 null이면 애초에 걸리지 않는다 — 지금 100%가
     * 아니거나(엔티티가 비운다) 시드처럼 도달 시각이 없는 행이다.
     *
     * <p>진척률을 함께 보는 것은 방어다: 시각과 진척률이 어긋나는 행이 생기면
     * (마이그레이션·직접 수정) 알림이 잘못 나가는 쪽보다 안 나가는 쪽이 낫다.
     */
    @Query("""
            select p from Project p
            where p.deleted = false
              and p.status = kr.proten.pms.project.ProjectStatus.IN_PROGRESS
              and p.progress = 100
              and p.hundredReachedAt is not null
              and p.hundredReachedAt <= :since
            order by p.hundredReachedAt asc
            """)
    List<Project> findCompletionOverdue(@Param("since") Instant since);

    Optional<Project> findByIdAndDeletedFalse(Long id);

    /** 중복 판정 (AC A1-2) — soft 삭제된 프로젝트는 대상에서 빠진다. */
    boolean existsByNormalizedClientAndNormalizedNameAndDeletedFalse(
            String normalizedClient,
            String normalizedName);

    /** 수정 시의 중복 판정 (AC A5-1) — 자기 자신은 대상에서 빠진다. */
    boolean existsByNormalizedClientAndNormalizedNameAndDeletedFalseAndIdNot(
            String normalizedClient,
            String normalizedName,
            Long id);

    Page<Project> findByDeletedFalse(Pageable pageable);

    Page<Project> findByIdInAndDeletedFalse(Collection<Long> ids, Pageable pageable);

    /**
     * phase 탭 필터 (AC A3-1 · §7 `?phase=`) — 갈래 둘은 위와 같고 상태 집합만 더 건다.
     *
     * <p>집합을 호출자가 짜 오지 않는다: {@code ProjectPhase.statuses()}가 정본이고
     * (§5 파생 정의), 저장소는 그것을 그대로 받는다. 여기에 상태를 다시 나열하면
     * 단건 응답의 phase와 목록 필터가 서로 다른 표를 보게 된다.
     *
     * <p>{@code search}(MCP {@code search_projects})를 빌려 쓰지 않는 이유는 그쪽이
     * {@code order by}를 질의 안에 고정하기 때문이다 — 웹 목록은 호출자의
     * {@code Pageable} 정렬을 따른다(§7 {@code ?sort=}).
     */
    Page<Project> findByDeletedFalseAndStatusIn(
            Collection<ProjectStatus> statuses, Pageable pageable);

    Page<Project> findByIdInAndDeletedFalseAndStatusIn(
            Collection<Long> ids, Collection<ProjectStatus> statuses, Pageable pageable);

    /**
     * 가시성 범위 안에서 상태·키워드로 거른 목록 (AC A3-1 · MCP {@code search_projects}).
     *
     * <p>{@code allVisible=true}면 프로젝트 id 집합을 보지 않는다 — 전사 가시성
     * (관리자·대표)에서 382건 id를 실어 보내지 않기 위한 갈래다.
     *
     * <p>키워드는 <b>이름·고객사·솔루션</b>을 본다(도구 description과 같은 범위).
     * 선택 필터의 null 판정에 {@code cast(... as ...)}로 타입을 주는 이유: 타입 없는
     * null을 PostgreSQL에 보내면 연산자를 찾지 못한다("No function matches").
     */
    @Query("""
            select p from Project p
            where p.deleted = false
              and (:allVisible = true or p.id in :visibleIds)
              and (cast(:statusName as string) is null or cast(p.status as string) = :statusName)
              and (cast(:keywordPattern as string) is null
                   or lower(p.name) like :keywordPattern
                   or lower(p.client) like :keywordPattern
                   or lower(p.solution) like :keywordPattern)
            order by p.startDate desc nulls last, p.id desc
            """)
    Page<Project> search(
            @Param("allVisible") boolean allVisible,
            @Param("visibleIds") Collection<Long> visibleIds,
            @Param("statusName") String statusName,
            @Param("keywordPattern") String keywordPattern,
            Pageable pageable);
}
