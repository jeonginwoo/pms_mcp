package kr.proten.pms.person.service.impl;

import kr.proten.pms.audit.AuditQueryService;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.person.service.AuditViewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 감사 로그 조회 — 권한 판정은 여기서 하고, 조회 자체는 아직 골격이다
 * (2026-08-22).
 *
 * 판정이 먼저 서 있는 이유: 플래그 없는 호출자에게 조직·계정 변경 이력이 새는 것은
 * 나중에 고칠 성질이 아니고, 판정이 있으면 그 성질을 지금부터 테스트할 수 있다.
 * 데이터 경로만 audit의 미구현 지점으로 내려간다.
 */
@Service
@Transactional(readOnly = true)
class AuditViewServiceImpl implements AuditViewService {
    private final AuditQueryService auditQueryService;
    private final OrgManagePermission orgManagePermission;

    AuditViewServiceImpl(
            AuditQueryService auditQueryService,
            OrgManagePermission orgManagePermission) {
        this.auditQueryService = auditQueryService;
        this.orgManagePermission = orgManagePermission;
    }

    @Override
    public Page<AuditRecord> listAll(long callerPersonId, Pageable pageable) {
        orgManagePermission.require(callerPersonId);

        return auditQueryService.findAll(pageable);
    }
}
