package kr.proten.pms.audit.service.impl;

import kr.proten.pms.audit.AuditQueryService;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.audit.repository.AuditLogRepository;
import kr.proten.pms.common.exception.NotImplementedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 조회 — **골격만 서 있고 로직은 아직 없다** (2026-08-22).
 *
 * 이미 정해져 있는 것:
 * - 두 뷰는 같은 테이블의 필터 차이뿐이다 — 이중 저장 금지(2026-08-06 결정)
 * - 정렬은 언제나 `createdAt` 최신순이고, 그 인덱스가 V3에 이미 있다
 * - 권한은 호출자가 판정한 뒤 들어온다(계약 주석 참조)
 *
 * TODO(G1-3·G2-2): `AuditLogRepository`에 최신순 파생 질의(`findAllByOrderBy…`,
 *   `findByProjectIdOrderBy…`)와 `AuditLog` → `AuditRecord` 변환이 필요하다.
 *   변환에는 before/after JSON 역직렬화가 들어가는데, 직렬화 쪽은 이미
 *   `AuditTrailImpl`이 하고 있으므로 그 지점을 함께 쓴다(두 곳에서 다른 규칙으로
 *   읽고 쓰면 이력이 깨진다).
 */
@Service
@Transactional(readOnly = true)
class AuditQueryServiceImpl implements AuditQueryService {
    private final AuditLogRepository auditLogRepository;

    AuditQueryServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public Page<AuditRecord> findAll(Pageable pageable) {
        throw new NotImplementedException("통합 감사 로그 조회 (G1-3)");
    }

    @Override
    public Page<AuditRecord> findByProject(long projectId, Pageable pageable) {
        throw new NotImplementedException("프로젝트별 변경 이력 조회 (G2-2)");
    }
}
