package kr.proten.pms.audit.repository;

import kr.proten.pms.audit.service.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 감사 로그 저장소 — append-only(G1-2)라 수정·삭제 경로를 만들지 않는다.
 *
 * <p>정렬을 <b>메서드 이름에 박아 둔다</b>: 두 조회 뷰의 AC가 모두 최신순이고
 * (G1-3·G2-2), 정렬을 호출자의 `Pageable`에 맡기면 `?sort=` 하나로 이력의 순서가
 * 바뀐다 — 이력은 시간 순서가 의미의 일부다. 서비스가 정렬 없는 `Pageable`을
 * 넘기므로 여기 선언한 순서만 적용된다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** 통합 로그 (G1-3) — `ix_audit_created`(V8)가 첫 페이지를 받는다. */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 프로젝트별 이력 (G2-2) — `ix_audit_project`(V3)가 그대로 맞는 인덱스다. */
    Page<AuditLog> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
}
