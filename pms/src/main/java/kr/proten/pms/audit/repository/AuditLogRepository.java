package kr.proten.pms.audit.repository;

import kr.proten.pms.audit.service.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 감사 로그 저장소.
 * 조회 뷰(G1-3·G2-2)가 들어올 때 최신순 파생 질의가 여기 붙는다 — 지금은 기록만
 * 하므로 쓰기 외의 질의를 미리 만들지 않는다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
