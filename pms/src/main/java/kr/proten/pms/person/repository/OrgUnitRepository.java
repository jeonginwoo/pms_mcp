package kr.proten.pms.person.repository;

import kr.proten.pms.person.service.entity.OrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 조직 노드 저장소. 트리 탐색은 전체 로드 후 {@link kr.proten.pms.person.service.entity.OrgTree}가
 * 담당한다 — 노드 수십 개 규모의 단순·표준 선택(OrgTree 주석 참조).
 */
public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {

    /** 하위 노드 수 — 빈 노드만 삭제 가능하다는 규칙(AC E3-3)의 판정. */
    long countByParentId(Long parentId);

    /**
     * 다음 노드 id — **시퀀스**에서 받는다 (AC E3-1).
     *
     * `max(id)+1`을 쓰지 않는 이유: 조직 노드는 유일하게 하드 삭제되는 참조 데이터라
     * 삭제된 id가 다시 발급되고, 그 노드를 가리키던 비활성 인원이 엉뚱한 새 조직에
     * 붙는다(2026-08-22 실기동에서 발견). 시퀀스는 되돌아가지 않는다.
     */
    @Query(value = "select nextval('org_unit_id_seq')", nativeQuery = true)
    long nextId();
}
