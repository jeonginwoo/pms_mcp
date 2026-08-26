package kr.proten.pms.maintenance.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
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
    private final IssueWriteGuard issueWriteGuard;
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
            IssueWriteGuard issueWriteGuard,
            Clock clock,
            ApplicationEventPublisher events) {
        this.issueRepository = issueRepository;
        this.siteRepository = siteRepository;
        this.commentRepository = commentRepository;
        this.queryService = queryService;
        this.viewFactory = viewFactory;
        this.personDirectoryService = personDirectoryService;
        this.auditRecorder = auditRecorder;
        this.issueWriteGuard = issueWriteGuard;
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
                // 본문은 선택이다 — 시드 267건이 본문 없이 살아 있고 그것이 정상이다
                blankToNull(command.content()),
                IssueStatus.RECEIVED,
                site.getEngineerId(),
                // 등록자를 남긴다 (2026-08-26) — 정정 권한이 이 값을 본다
                callerPersonId,
                today(),
                null)));
        auditRecorder.issueCreated(callerPersonId, issue);
        // 구독자는 커밋 후에 돈다(@ApplicationModuleListener) — 롤백되면 알림도 없다
        events.publishEvent(new MaintenanceIssueRegistered(
                issue.getId(), issue.getTitle(), issue.getAssigneeId(), site.getName()));

        return queryService.getIssue(issue.getId());
    }

    /**
     * 이슈 처리·정정 (AC D3-2 상태·담당 + AC D3-5 제목·유형·본문 — 2026-08-26).
     *
     * <p>락을 입력 검사보다 먼저 보는 것은 계약 쓰기와 같은 이유다: "다른 사람이 이미
     * 바꿨다"는 내 입력이 옳은지와 무관한 사실이다.
     *
     * <p><b>관문이 조건부다</b>: 상태 전이·재배정은 US-D3대로 로그인 사용자 전체가 하고
     * <b>제목·유형·본문 정정에만</b> {@link IssueWriteGuard}가 걸린다. 갈라야 하는 것은
     * <b>행위</b>다 — 처리는 현장의 일이고 정정은 남이 쓴 글을 고치는 일이다. 라우트를
     * 둘로 쪼개지 않은 이유는 "상태와 제목을 한 번에" 요청이 두 왕복이 되고 락도 두 번
     * 돌기 때문이다.
     */
    @Override
    public IssueView process(
            long callerPersonId, long issueId, IssueEditCommand command, long version) {
        MaintenanceIssue issue =
                issueRepository.findActiveById(issueId).orElseThrow(NotFoundException::new);
        issue.requireVersion(version);

        if (command.isEmpty()) {
            throw new ValidationException("바꿀 내용이 없습니다", "status");
        }

        // 정정 칸이 실려 있으면 그 관문을 먼저 지난다 — 권한은 입력·참조보다 앞이다
        if (isCorrection(command)) {
            issueWriteGuard.require(callerPersonId, issue);
        }

        requirePerson(command.assigneeId(), "assigneeId");
        requireEditableTitle(command.title());
        // 바꾸기 직전에 떠 둔다 — 바뀐 필드만 이력에 남는다
        Map<String, Object> before = auditRecorder.snapshot(issue);

        if (command.status() != null) {
            issue.changeStatus(command.status(), today());
        }

        if (command.assigneeId() != null) {
            issue.reassign(command.assigneeId());
        }

        issue.edit(command.type(), trimOrNull(command.title()), command.content());

        // flush 해야 응답의 version이 커밋 뒤의 값이 된다 — 안 하면 호출자가 그 값으로
        // 다시 수정하려다 409를 받는다(계약·사이트가 같은 이유로 saveAndFlush)
        MaintenanceIssue saved = issueRepository.saveAndFlush(issue);
        auditRecorder.issueChanged(callerPersonId, saved, before);

        return queryService.getIssue(issueId);
    }

    /**
     * 이슈 삭제 (AC D3-6 — 2026-08-26 신설). <b>soft 삭제</b>다: 프로젝트 A4 선례이고,
     * 행을 지우면 코멘트·감사가 가리키는 대상이 사라진다.
     *
     * <p>version을 받는 이유는 처리와 같다 — 남이 방금 상태를 옮긴 이슈를 모르고 지우는
     * 일을 막는다. 되돌리기 경로는 없다(AC에 요구가 없다).
     */
    @Override
    public void delete(long callerPersonId, long issueId, long version) {
        MaintenanceIssue issue =
                issueRepository.findActiveById(issueId).orElseThrow(NotFoundException::new);
        issue.requireVersion(version);
        issueWriteGuard.require(callerPersonId, issue);

        Map<String, Object> before = auditRecorder.snapshot(issue);
        issue.delete();
        auditRecorder.issueChanged(callerPersonId, issueRepository.saveAndFlush(issue), before);
    }

    /**
     * 코멘트 추가 (AC D3-3).
     *
     * <p><b>감사 행을 남기지 않는다</b>: 코멘트 자체가 "누가 언제 무엇을 적었나"를
     * 담은 기록이라, 감사에 또 남기면 같은 사실이 두 표에 두 벌 생긴다.
     * 감사가 답하는 질문(무엇이 바뀌었나)에 코멘트는 해당하지 않는다 — 이슈는
     * 바뀌지 않았고 사실이 하나 쌓였다.
     */
    @Override
    public CommentView addComment(long callerPersonId, long issueId, String content) {
        requireActiveIssue(issueId);

        String text = required(content, "코멘트 내용은 필수입니다", "content");
        IssueComment comment = commentRepository.save(
                IssueComment.of(issueId, callerPersonId, text, Instant.now(clock)));

        return viewOf(comment);
    }

    /**
     * 코멘트 수정 (AC D3-7 — 2026-08-26 신설) — <b>작성자 본인만</b>.
     *
     * <p>append-only 폐기의 범위가 여기까지다(사용자 결정): 남의 이력을 고치는 길은
     * 열지 않는다. 판정을 엔티티에 물어보는 이유는 그 규칙이 코멘트의 성질이라서다.
     *
     * <p>이슈가 삭제됐으면 404다 — 그 이슈의 코멘트는 화면에 나올 자리가 없다.
     */
    @Override
    public CommentView editComment(long callerPersonId, long commentId, String content) {
        IssueComment comment = requireOwnComment(callerPersonId, commentId);
        String text = required(content, "코멘트 내용은 필수입니다", "content");

        comment.rewrite(text, Instant.now(clock));

        return viewOf(commentRepository.saveAndFlush(comment));
    }

    /**
     * 코멘트 삭제 (AC D3-7 — 2026-08-26 신설) — <b>작성자 본인만</b>, 행을 지운다.
     *
     * <p>tombstone(삭제 표시)안은 미채택이다(사용자 결정). 이슈 삭제와 방식이 다른 것은
     * 가리키는 것이 없기 때문이다 — 코멘트를 참조하는 표가 없다.
     */
    @Override
    public void deleteComment(long callerPersonId, long commentId) {
        commentRepository.delete(requireOwnComment(callerPersonId, commentId));
    }

    /**
     * 내 코멘트인가 — 아니면 403이고, 없거나 삭제된 이슈의 것이면 404다.
     *
     * <p>순서가 규칙이다: 존재(404)를 권한(403)보다 먼저 본다. 뒤집으면 남의 코멘트
     * id를 넣어 보며 "있다/없다"를 403과 404로 헤아릴 수 있다.
     */
    private IssueComment requireOwnComment(long callerPersonId, long commentId) {
        IssueComment comment =
                commentRepository.findById(commentId).orElseThrow(NotFoundException::new);
        requireActiveIssue(comment.getIssueId());

        if (!comment.isAuthoredBy(callerPersonId)) {
            throw new ForbiddenException("자기가 남긴 코멘트만 수정·삭제할 수 있습니다");
        }

        return comment;
    }

    /** 살아 있는 이슈인가 — 삭제와 부재는 같은 404다(AC D3-6). */
    private void requireActiveIssue(long issueId) {
        if (issueRepository.findActiveById(issueId).isEmpty()) {
            throw new NotFoundException();
        }
    }

    private CommentView viewOf(IssueComment comment) {
        return new CommentView(
                comment.getId(),
                authorRef(comment.getAuthorId()),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }

    /** 정정 칸(제목·유형·본문)이 실려 있는가 — 관문이 이 판정으로 갈린다. */
    private boolean isCorrection(IssueEditCommand command) {
        return command.type() != null || command.title() != null || command.content() != null;
    }

    /** 제목은 있으면 비어 있지 않아야 한다 — null은 "그대로"이고 공백은 오류다. */
    private void requireEditableTitle(String title) {
        if (title != null && title.isBlank()) {
            throw new ValidationException("제목은 비울 수 없습니다", "title");
        }
    }

    private String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
