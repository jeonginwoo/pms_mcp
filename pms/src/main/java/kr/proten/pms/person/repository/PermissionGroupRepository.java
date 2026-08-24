package kr.proten.pms.person.repository;

import kr.proten.pms.person.service.entity.PermissionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 권한 그룹 저장소. */
public interface PermissionGroupRepository extends JpaRepository<PermissionGroup, Long> {

    /**
     * 다음 권한 그룹 id — **시퀀스**에서 받는다 (AC E5-1).
     *
     * 조직 노드와 같은 이유다: 사용 중이 아니면 하드 삭제되므로 `max(id)+1`은 삭제된
     * id를 다시 내주고, 그 행을 가리키던 비활성 인원·감사 로그가 엉뚱한 것을 가리키게
     * 된다(2026-08-22 조직 노드 실측 사고 — V5·V6). 규칙 원본은 PRD-pms 부록 B다.
     */
    @Query(value = "select nextval('permission_group_id_seq')", nativeQuery = true)
    long nextId();
}
