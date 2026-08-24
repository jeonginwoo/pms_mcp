package kr.proten.pms.maintenance.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.MaintenanceIssueRegistered;
import kr.proten.pms.maintenance.repository.IssueCommentRepository;
import kr.proten.pms.maintenance.repository.MaintenanceIssueRepository;
import kr.proten.pms.maintenance.repository.MaintenanceSiteRepository;
import kr.proten.pms.maintenance.service.IssueCommandService;
import kr.proten.pms.maintenance.service.IssueQueryService;
import kr.proten.pms.maintenance.service.dto.CommentView;
import kr.proten.pms.maintenance.service.dto.IssueCommand;
import kr.proten.pms.maintenance.service.dto.IssueEditCommand;
import kr.proten.pms.maintenance.service.dto.IssueView;
import kr.proten.pms.maintenance.service.entity.IssueComment;
import kr.proten.pms.maintenance.service.entity.IssueProfile;
import kr.proten.pms.maintenance.service.entity.IssueStatus;
import kr.proten.pms.maintenance.service.entity.MaintenanceIssue;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.person.PersonRef;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유지보수 이슈 쓰기 (US-D3).
 *
 * <p>세 유스케이스가 계약 쓰기와 <b>같은 순서를 밟되 첫 칸이 비어 있다</b> —
 * (권한) → 존재(404) → 낙관적 락(409) → 입력·참조(400·422) → 변경 → 감사 → 발행.
 * 권한 칸이 없는 것은 US-D3이 로그인 사용자 전체이기 때문이고, 그래서
 * {@code ContractWriteGuard}가 여기 주입되지 않는다(계약과 다른 사실이다).
 *
 * <p>이슈 id는 {@code max(id)+1}이다 — 계약과 같은 칸이다(PRD-pms §4 참조 데이터 id
 * 발급 규칙: 하드 삭제가 없으면 id가 회수되지 않아 시퀀스까지 필요하지 않다).
 * 시드가 원본 이슈 번호 230~496을 쓰므로 새 이슈는 그 위에서 시작한다.
 *
 * <p>응답은 조회 계약을 그대로 부른다 — 사이트·계약명·담당자·코멘트를 붙이는 조립을
 * 여기서 다시 하면 정본이 두 벌이 되고, 화면이 등록 직후와 새로고침 후에 다른 것을
 * 본다(계약 쓰기가 같은 판단을 이미 적어 뒀다).
 */
@Service
@Transactional
class IssueCommandServiceImpl implements IssueCommandService {
    private final MaintenanceIssueRepository issueRepository;
    private final MaintenanceSiteRepository siteRepository;
    private final IssueCommentRepository commentRepository;
    private final IssueQueryService queryService;
    private final MaintenanceViewFactory viewFactory;
    private final PersonDirectoryService personDirectoryService;
    private final MaintenanceAuditRecorder auditRecorder;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    IssueCommandServiceImpl(
            MaintenanceIssueRepository issueRepository,
            MaintenanceSiteRepository siteRepository,
            IssueCommentRepository commentRepository,
            IssueQueryService queryService,
            MaintenanceViewFactory viewFactory,
            PersonDirectoryService personDirectoryService,
            MaintenanceAuditRecorder auditRecorder,
            Clock clock,
            ApplicationEventPublisher events) {
        this.issueRepository = issueRepository;
        this.siteRepository = siteRepository;
        this.commentRepository = commentRepository;
        this.queryService = queryService;
        this.viewFactory = viewFactory;
        this.personDirectoryService = personDirectoryService;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
        this.events = events;
    }

    /**
     * 이슈 등록 (AC D3-1).
     *
     * <p>담당자는 <b>사이트에서 온다</b> — 요청에 그 칸이 없다({@code IssueCommand} 주석).
     * 사이트에 담당 엔지니어가 없으면 미배정으로 남고, 그것이 D3-4 미배정 필터가
     * 찾는 상태다.
     */
    @Override
    public IssueView register(long callerPersonId, IssueCommand command) {
        // 입력 검사가 참조 조회보다 앞이다 — 계약 쓰기와 같은 순서(400 → 422).
        // 뒤집으면 "없는 사이트 + 빈 제목" 요청이 400 대신 422를 받는다
        validate(command);
        MaintenanceSite site = siteOf(command);

        MaintenanceIssue issue = issueRepository.save(MaintenanceIssue.of(new IssueProfile(
                issueRepository.nextId(),
                site.getId(),
                command.type(),
                command.title().trim(),
                IssueStatus.RECEIVED,
                site.getEngineerId(),
                today(),
                null)));
        auditRecorder.issueCreated(callerPersonId, issue);
        // 구독자는 커밋 후에 돈다(@ApplicationModuleListener) — 롤백되면 알림도 없다
        events.publishEvent(new MaintenanceIssueRegistered(
                issue.getId(), issue.getTitle(), issue.getAssigneeId(), site.getName()));

        return queryService.getIssue(issue.getId());
    }

    /**
     * 이슈 처리 (AC D3-2) — 상태 전이·담당 재배정.
     *
     * <p>락을 입력 검사보다 먼저 보는 것은 계약 쓰기와 같은 이유다: "다른 사람이 이미
     * 바꿨다"는 내 입력이 옳은지와 무관한 사실이다.
     */
    @Override
    public IssueView process(
            long callerPersonId, long issueId, IssueEditCommand command, long version) {
        MaintenanceIssue issue =
                issueRepository.findById(issueId).orElseThrow(NotFoundException::new);
        issue.requireVersion(version);
        requirePerson(command.assigneeId(), "assigneeId");
        // 바꾸기 직전에 떠 둔다 — 바뀐 필드만 이력에 남는다
        Map<String, Object> before = auditRecorder.snapshot(issue);

        if (command.status() != null) {
            issue.changeStatus(command.status(), today());
        }

        if (command.assigneeId() != null) {
            issue.reassign(command.assigneeId());
        }

        // flush 해야 응답의 version이 커밋 뒤의 값이 된다 — 안 하면 호출자가 그 값으로
        // 다시 수정하려다 409를 받는다(계약·사이트가 같은 이유로 saveAndFlush)
        MaintenanceIssue saved = issueRepository.saveAndFlush(issue);
        auditRecorder.issueChanged(callerPersonId, saved, before);

        return queryService.getIssue(issueId);
    }

    /**
     * 코멘트 추가 (AC D3-3) — append-only.
     *
     * <p><b>감사 행을 남기지 않는다</b>: 코멘트 자체가 "누가 언제 무엇을 적었나"를
     * 담은 불변 기록이라, 감사에 또 남기면 같은 사실이 두 표에 두 벌 생긴다.
     * 감사가 답하는 질문(무엇이 바뀌었나)에 코멘트는 해당하지 않는다 — 이슈는
     * 바뀌지 않았고 사실이 하나 쌓였다.
     */
    @Override
    public CommentView addComment(long callerPersonId, long issueId, String content) {
        if (!issueRepository.existsById(issueId)) {
            throw new NotFoundException();
        }

        String text = required(content, "코멘트 내용은 필수입니다", "content");
        IssueComment comment = commentRepository.save(
                IssueComment.of(issueId, callerPersonId, text, Instant.now(clock)));

        return new CommentView(
                comment.getId(), authorRef(callerPersonId), comment.getContent(),
                comment.getCreatedAt());
    }

    /**
     * 등록 대상 사이트를 읽는다 — 없는 사이트를 지정하면 422다(계약 쓰기의 참조 검증과
     * 같은 규칙). {@code siteId} 자체가 비었는지는 {@link #validate}가 먼저 본다:
     * 지정이 없는 것은 입력 오류(400)이고 지정이 틀린 것은 참조 오류(422)다.
     */
    private MaintenanceSite siteOf(IssueCommand command) {
        return siteRepository.findById(command.siteId())
                .orElseThrow(() -> new UnprocessableException(
                        ErrorCode.REF_NOT_FOUND, "존재하지 않는 사이트입니다 — siteId"));
    }

    /** 필수 입력 — 셋 다 400이고, 참조 검증보다 앞선다. */
    private void validate(IssueCommand command) {
        if (command.siteId() == null) {
            throw new ValidationException("사이트는 필수입니다", "siteId");
        }

        if (command.type() == null) {
            throw new ValidationException("이슈 유형은 필수입니다", "type");
        }

        required(command.title(), "제목은 필수입니다", "title");
    }

    /** 참조 검증 — 미지정(null)은 정상이고, 지정했는데 없는 인원이면 422다. */
    private void requirePerson(Long personId, String field) {
        if (personId == null) {
            return;
        }

        if (!personDirectoryService.existsActive(personId)) {
            throw new UnprocessableException(
                    ErrorCode.REF_NOT_FOUND, "존재하지 않는 인원입니다 — " + field);
        }
    }

    /**
     * 작성자 참조 — 조회 계약을 부르지 않는 유일한 자리다. 코멘트 한 건의 응답에
     * 이슈 전체(사이트·계약·코멘트 전량)를 다시 조립할 이유가 없다.
     */
    private PersonRef authorRef(long personId) {
        return viewFactory.refsOf(List.of(personId)).get(personId);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String required(String value, String message, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message, field);
        }

        return value.trim();
    }
}
