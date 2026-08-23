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
    long countByOrgUnitIdAndActiveTrue(Long orgUnitId);
}
