package kr.proten.pms.project.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.proten.pms.project.MonthlyAssignment;
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

    /**
     * 그 달과 겹치는 배정 — 가동률 분자의 원천 (AC C1-1, 모듈 밖 계약
     * {@link kr.proten.pms.project.AssignmentDirectoryService}가 이 질의를 쓴다).
     *
     * <p>상태로 거르지 않고 <b>기간 겹침</b>만 본다: 종료 시 {@code endDate}가 종료월
     * 말일로 당겨지므로(AC B2-1) 겹침 판정 하나로 "종료월 이후 제외"가 성립하고,
     * 지난달을 오늘 조회해도 그때의 수치가 재현된다. 상태를 함께 보면 오늘 종료한
     * 배정이 지난달 집계에서까지 사라진다.
     *
     * <p>기간이 열린 배정(경계가 null)은 그 방향으로 무한하다고 본다.
     * 이름을 동봉하려고 {@code Project}를 함께 읽는다 — 호출자가 id로 되묻게 하면
     * N+1이 모듈 경계를 넘어 생긴다.
     */
    @Query("""
            select new kr.proten.pms.project.MonthlyAssignment(
                    a.personId, a.projectId, p.name, p.status, a.monthlyMm)
            from ProjectAssignment a, Project p
            where p.id = a.projectId
              and a.personId in :personIds
              and (a.startDate is null or a.startDate <= :monthEnd)
              and (a.endDate is null or a.endDate >= :monthStart)
            """)
    List<MonthlyAssignment> findOverlapping(
            @Param("personIds") Collection<Long> personIds,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);
}
