package kr.proten.pms.project.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 프로젝트 배정 저장소. */
public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {

    List<ProjectAssignment> findByProjectIdAndStatus(Long projectId, AssignmentStatus status);

    Optional<ProjectAssignment> findByProjectIdAndPersonIdAndStatus(
            Long projectId,
            Long personId,
            AssignmentStatus status);

    /** 이 프로젝트의 특정 역할 배정 — PM 1행 불변식(A6-5) 유지의 원천. */
    List<ProjectAssignment> findByProjectIdAndRoleAndStatus(
            Long projectId,
            ProjectRole role,
            AssignmentStatus status);

    /** 중복 배정 판정 (AC B1-2) — 종료된 배정은 재배정을 막지 않는다. */
    boolean existsByProjectIdAndPersonIdAndStatus(
            Long projectId,
            Long personId,
            AssignmentStatus status);

    /**
     * 주어진 인원이 배정된 프로젝트 id — 프로젝트 가시성의 원천 (상위 PRD §4-4).
     * 배정 행 전체가 아니라 id만 올린다 — 필요한 것이 집합이므로 행을 실을 이유가 없다.
     */
    @Query("""
            select distinct a.projectId
            from ProjectAssignment a
            where a.personId in :personIds and a.status = :status
            """)
    List<Long> findDistinctProjectIdsByPersonIds(
            @Param("personIds") Collection<Long> personIds,
            @Param("status") AssignmentStatus status);
}
