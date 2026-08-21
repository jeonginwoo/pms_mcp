package kr.proten.pms.project.repository;

import java.util.Collection;
import java.util.Optional;
import kr.proten.pms.project.service.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
