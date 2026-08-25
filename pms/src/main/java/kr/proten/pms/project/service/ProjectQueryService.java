package kr.proten.pms.project.service;

import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.ProjectSummary;
import kr.proten.pms.project.service.entity.ProjectPhase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 프로젝트 조회 — AC A3-1~A3-3 · G2-2.
 *
 * 세 조회가 한 계약인 이유는 판정이 하나이기 때문이다: 전부 **프로젝트 가시성**으로
 * 걸러지고, 범위 밖은 403이 아니라 404로 은닉된다. 이력 조회를 따로 두지 않는 것도
 * 같은 이유다 — 이력을 못 봐서 404가 아니라 그 프로젝트를 못 봐서 404다(G2-3).
 */
public interface ProjectQueryService {

    /**
     * 가시성 범위 내 목록 (AC A3-1) — 조직 범위와 본인 배정의 합집합이다.
     *
     * <p>{@code phase}는 선택이다(§7 {@code ?phase=}). null이면 그룹을 따지지 않으므로
     * 유지보수중까지 전부 나오고, 값이 있으면 그 그룹의 상태들만 남는다 —
     * <b>유지보수중은 어느 값으로도 나오지 않는다</b>(§5: 유지보수 탭의 원천은
     * 프로젝트가 아니라 계약이다).
     *
     * <p>필터는 가시성을 넓히지도 좁히지도 않는다 — 판정이 먼저고 그룹이 나중이다.
     */
    Page<ProjectSummary> listVisible(long callerPersonId, ProjectPhase phase, Pageable pageable);

    /** 단건 조회 (AC A3-2·A3-3) — 배정과 파생 phase를 함께 담는다. */
    ProjectDetail getProject(long callerPersonId, long projectId);

    /**
     * 프로젝트별 변경 이력 최신순 (AC G2-2).
     * 가시성 안이면 역할은 따지지 않는다 — 참여자도 본다(2026-08-06 확정).
     */
    Page<AuditRecord> listAudit(long callerPersonId, long projectId, Pageable pageable);
}
