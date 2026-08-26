package kr.proten.pms.project.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.proten.pms.person.LiveAssignment;
import kr.proten.pms.project.MonthlyAssignment;
import kr.proten.pms.project.ProjectStatus;
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

    /**
     * 이 사람이 <b>지금 물려 있는</b> 배정 건수 (person `AssignmentCountPort` — 이동 경고 E1-2).
     *
     * <p><b>배정 상태만으로는 답이 되지 않는다</b>(2026-08-26 실측·수정): 완료 프로젝트의
     * 배정이 {@code ACTIVE}로 남아 있어 — 462건 중 384건 — 옛 질의는 한 사람에게
     * "진행 중인 배정 128건"이라 답했다(실제 5건). 프로젝트 상태를 함께 보는 이유는
     * {@code ProjectStatus.LIVE}의 javadoc에 있다.
     */
    @Query("""
            select count(a)
            from ProjectAssignment a, Project p
            where p.id = a.projectId
              and a.personId = :personId
              and a.status = :status
              and p.status in :liveStatuses
            """)
    long countLiveByPerson(
            @Param("personId") Long personId,
            @Param("status") AssignmentStatus status,
            @Param("liveStatuses") Collection<ProjectStatus> liveStatuses);

    /**
     * 이 사람이 지금 물려 있는 배정 — 퇴사 처리의 안내·판정 원천
     * (person {@link kr.proten.pms.person.AssignmentReleasePort}).
     *
     * <p>위 건수 질의와 조건이 같고 <b>행을 싣는 것만 다르다</b>. 정렬을 질의가 정하는
     * 이유는 안내 문구가 실행마다 달라지지 않게 하기 위해서다(감사 조회에서 정렬을
     * 저장소 메서드 이름이 정한 것과 같은 규율).
     */
    @Query("""
            select new kr.proten.pms.person.LiveAssignment(
                    a.projectId, p.name, a.role = kr.proten.pms.project.service.entity.ProjectRole.PM)
            from ProjectAssignment a, Project p
            where p.id = a.projectId
              and a.personId = :personId
              and a.status = :status
              and p.status in :liveStatuses
            order by p.name asc
            """)
    List<LiveAssignment> findLiveByPerson(
            @Param("personId") Long personId,
            @Param("status") AssignmentStatus status,
            @Param("liveStatuses") Collection<ProjectStatus> liveStatuses);

    /**
     * 이 사람이 지금 물려 있는 배정 행 — 종료 처리의 대상
     * (person {@link kr.proten.pms.person.AssignmentReleasePort#closeParticipantAssignments}).
     *
     * <p>위 둘과 조건이 같지만 <b>엔티티를 싣는다</b>: 종료는 {@code close()}를 거쳐야
     * 하고(AC B2-1의 endDate 당김이 거기 있다) 감사 스냅샷도 엔티티에서 뜬다.
     */
    @Query("""
            select a
            from ProjectAssignment a, Project p
            where p.id = a.projectId
              and a.personId = :personId
              and a.status = :status
              and p.status in :liveStatuses
            """)
    List<ProjectAssignment> findLiveEntitiesByPerson(
            @Param("personId") Long personId,
            @Param("status") AssignmentStatus status,
            @Param("liveStatuses") Collection<ProjectStatus> liveStatuses);

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
