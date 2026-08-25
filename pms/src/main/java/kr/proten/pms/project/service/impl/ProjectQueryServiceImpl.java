package kr.proten.pms.project.service.impl;

import kr.proten.pms.audit.AuditQueryService;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.ProjectSummary;
import kr.proten.pms.project.service.dto.ProjectVisibility;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectPhase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 조회 유스케이스 — AC A3-1~A3-3 · G2-2.
 * 가시성 필터는 질의 조건으로 내려가고(전체 로드 후 메모리 필터 금지) 단건은
 * 가시성 밖을 404로 은닉한다 — 판정 자체는 ProjectVisibilityService가 소유한다.
 */
@Service
@Transactional(readOnly = true)
public class ProjectQueryServiceImpl implements ProjectQueryService {
    private final ProjectRepository projectRepository;
    private final ProjectVisibilityService projectVisibilityService;
    private final ProjectViewFactory projectViewFactory;
    private final AuditQueryService auditQueryService;

    public ProjectQueryServiceImpl(
            ProjectRepository projectRepository,
            ProjectVisibilityService projectVisibilityService,
            ProjectViewFactory projectViewFactory,
            AuditQueryService auditQueryService) {
        this.projectRepository = projectRepository;
        this.projectVisibilityService = projectVisibilityService;
        this.projectViewFactory = projectViewFactory;
        this.auditQueryService = auditQueryService;
    }

    /**
     * 가시성 범위 내 프로젝트 목록 — 조직 범위와 본인 배정의 합집합이다.
     * phase는 그 위에 얹는 선택 필터이며 질의 조건으로 내려간다(§7 {@code ?phase=}).
     */
    public Page<ProjectSummary> listVisible(
            long callerPersonId, ProjectPhase phase, Pageable pageable) {
        ProjectVisibility visibility = projectVisibilityService.visibilityOf(callerPersonId);

        if (visibility.unrestricted()) {
            return toSummaryPage(phase == null
                    ? projectRepository.findByDeletedFalse(pageable)
                    : projectRepository.findByDeletedFalseAndStatusIn(phase.statuses(), pageable));
        }

        if (visibility.visibleProjectIds().isEmpty()) {
            return Page.empty(pageable);
        }

        return toSummaryPage(phase == null
                ? projectRepository.findByIdInAndDeletedFalse(
                        visibility.visibleProjectIds(), pageable)
                : projectRepository.findByIdInAndDeletedFalseAndStatusIn(
                        visibility.visibleProjectIds(), phase.statuses(), pageable));
    }

    /**
     * 프로젝트 단건 조회.
     * 배정 레코드는 타 팀 인원까지 그대로 노출한다 — 프로젝트 컨텍스트 안에서는
     * 조직 가시성이 확장되기 때문이다(상위 PRD §4-4).
     */
    public ProjectDetail getProject(long callerPersonId, long projectId) {
        Project project = projectVisibilityService.requireVisible(callerPersonId, projectId);

        return projectViewFactory.toDetail(project);
    }

    /**
     * 프로젝트별 변경 이력 (AC G2-2).
     *
     * 관문이 단건 조회와 **같은** `requireVisible`인 것이 G2-3의 구현이다 — 없는
     * 프로젝트와 가시성 밖 프로젝트가 같은 404로 수렴한다. 통과한 뒤에는 역할을
     * 따지지 않는다(참여자 포함 — 2026-08-06 확정): 대상 데이터를 볼 수 있는 사람은
     * 그 변경 사실도 본다.
     */
    public Page<AuditRecord> listAudit(long callerPersonId, long projectId, Pageable pageable) {
        projectVisibilityService.requireVisible(callerPersonId, projectId);

        return auditQueryService.findByProject(projectId, pageable);
    }

    private Page<ProjectSummary> toSummaryPage(Page<Project> projects) {
        return new PageImpl<>(
                projectViewFactory.toSummaries(projects.getContent()),
                projects.getPageable(),
                projects.getTotalElements());
    }
}
