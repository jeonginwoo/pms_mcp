package kr.proten.pms.project.repository;

import java.util.Collection;
import java.util.Optional;
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
