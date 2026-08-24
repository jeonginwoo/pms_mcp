package kr.proten.pms.person.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.proten.pms.person.service.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 인원 저장소. 목록 질의는 전부 가시성·노출 조건을 파생 질의로 내려 보낸다 —
 * 전체 로드 후 메모리 필터는 금지다(conventions/java-spring.md §6).
 */
public interface PersonRepository extends JpaRepository<Person, Long> {

    /**
     * 다음 인원 id — 인원은 시드 id를 보존하는 참조 데이터라 식별자를 생성 컬럼으로
     * 두지 않았다(엔티티가 id를 받는다). 그래서 신규 등록(AC E2-1)은 최대값 다음을 쓴다.
     */
    @Query("select coalesce(max(p.id), 0) + 1 from Person p")
    long nextId();

    Optional<Person> findByIdAndActiveTrue(Long id);

    boolean existsByIdAndActiveTrue(Long id);

    /** 가동률·인력 목록 노출 대상 — 시스템 계정과 비활성 인원은 제외한다. */
    List<Person> findByActiveTrueAndSystemFalseOrderByIdAsc();

    List<Person> findByIdInAndActiveTrueAndSystemFalseOrderByIdAsc(Collection<Long> ids);

    /** 가시성 조직 집합에 속한 활성 인원 — 가시 인원 id 집합의 원천. */
    List<Person> findByOrgUnitIdInAndActiveTrue(Collection<Long> orgUnitIds);

    List<Person> findByIdInAndActiveTrue(Collection<Long> ids);

    /** 조직별 인원 집계용 — 참조 데이터 44행 규모라 한 번에 올린다(OrgUnitServiceImpl 주석). */
    List<Person> findByActiveTrue();

    /** 이름으로 활성 인원 — 동명이인이면 여러 행이라 호출자가 유일성을 판정한다. */
    List<Person> findByNameAndActiveTrue(String name);

    /** 조직 노드 삭제 가능 판정 (AC E3-3) — 빈 노드만 지울 수 있다. */
    /** 같은 조직의 활성 인원 (AC F1-1 수신자 후보 — 플래그 판정은 서비스가 한다). */
    List<Person> findByOrgUnitIdAndActiveTrue(Long orgUnitId);

    long countByOrgUnitIdAndActiveTrue(Long orgUnitId);

    /**
     * 이 직급을 쓰는 인원이 있는가 (AC E4-3 `409 IN_USE`).
     *
     * <p><b>비활성 인원도 센다</b>: soft 삭제라 `grade_id`가 그대로 남아 있고, 지우면
     * 그 사람의 과거 직급이 사라진다. "쓰는 사람이 없다"는 판정이 살아 있는 인원만
     * 보면 참조가 남은 행을 지우게 된다.
     */
    boolean existsByGradeId(Long gradeId);

    /** 이 권한 그룹에 속한 인원이 있는가 (AC E5-4 `409 IN_USE`) — 위와 같은 이유로 비활성 포함. */
    boolean existsByGroupId(Long groupId);

    /**
     * 직급·권한 그룹별 인원 수 — 관리 화면이 "n명"과 삭제 버튼 노출에 쓴다(부록 A).
     *
     * <p>기준을 {@code existsBy…}와 <b>같게</b> 맞춘다(비활성 포함): 화면이 "0명"이라
     * 보여 준 행을 지웠는데 서버가 409를 내면 그것은 두 규칙이 갈린 것이다.
     *
     * <p>행마다 세지 않고 한 번에 묶어 받는다 — 직급 9·그룹 4개라 N+1이 눈에 띄지는
     * 않지만, 개수 질문을 목록마다 반복할 이유가 없다(conventions §6).
     */
    @Query("select p.gradeId, count(p) from Person p group by p.gradeId")
    List<Object[]> countByGrade();

    @Query("select p.groupId, count(p) from Person p group by p.groupId")
    List<Object[]> countByGroup();

    long countByGradeId(Long gradeId);

    long countByGroupId(Long groupId);
}
