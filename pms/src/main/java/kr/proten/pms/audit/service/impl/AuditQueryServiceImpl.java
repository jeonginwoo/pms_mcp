package kr.proten.pms.audit.service.impl;

import kr.proten.pms.audit.AuditQueryService;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.audit.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 조회 (G1-3 · G2-2) — 같은 테이블의 두 필터 뷰다.
 *
 * <p><b>판정하지 않는다</b>: 통합 로그의 관리 플래그(G1-3)와 프로젝트 가시성·404
 * 은닉(G2-2)은 호출자인 {@code person.AuditViewService}·{@code
 * project.ProjectQueryService.listAudit}이 이미 세워 두었다. 이 모듈이 다시 판정하면
 * person·project를 되참조해야 하고 그것은 모듈 순환이다 — 그래서 audit은 권한을
 * 모르는 순수 조회로 남는다(2026-08-22 결정).
 *
 * <p><b>정렬은 호출자에게 맡기지 않는다</b>: 두 AC가 모두 최신순이고, 이력은 시간
 * 순서가 의미의 일부라 `?sort=` 하나로 뒤집히면 안 된다. 들어온 `Pageable`에서
 * 페이지·크기만 취하고 순서는 저장소 메서드 이름이 정한다.
 */
@Service
@Transactional(readOnly = true)
class AuditQueryServiceImpl implements AuditQueryService {
    private final AuditLogRepository auditLogRepository;
    private final AuditRecordFactory auditRecordFactory;

    AuditQueryServiceImpl(
            AuditLogRepository auditLogRepository, AuditRecordFactory auditRecordFactory) {
        this.auditLogRepository = auditLogRepository;
        this.auditRecordFactory = auditRecordFactory;
    }

    @Override
    public Page<AuditRecord> findAll(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(unsorted(pageable))
                .map(auditRecordFactory::toRecord);
    }

    @Override
    public Page<AuditRecord> findByProject(long projectId, Pageable pageable) {
        return auditLogRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId, unsorted(pageable))
                .map(auditRecordFactory::toRecord);
    }

    /**
     * 정렬을 뗀 페이지 요청. 남겨 두면 파생 질의의 {@code OrderBy}에 호출자의 정렬이
     * 덧붙어 최신순이 두 번째 기준으로 밀린다.
     */
    private static Pageable unsorted(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}
