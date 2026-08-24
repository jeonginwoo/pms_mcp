package kr.proten.pms.maintenance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.StaleVersionException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.MaintenanceIssueRegistered;
import kr.proten.pms.maintenance.repository.IssueCommentRepository;
import kr.proten.pms.maintenance.repository.MaintenanceIssueRepository;
import kr.proten.pms.maintenance.repository.MaintenanceSiteRepository;
import kr.proten.pms.maintenance.service.IssueQueryService;
import kr.proten.pms.maintenance.service.dto.CommentView;
import kr.proten.pms.maintenance.service.dto.IssueCommand;
import kr.proten.pms.maintenance.service.dto.IssueEditCommand;
import kr.proten.pms.maintenance.service.dto.IssueView;
import kr.proten.pms.maintenance.service.entity.IssueComment;
import kr.proten.pms.maintenance.service.entity.IssueProfile;
import kr.proten.pms.maintenance.service.entity.IssueStatus;
import kr.proten.pms.maintenance.service.entity.IssueType;
import kr.proten.pms.maintenance.service.entity.MaintenanceIssue;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import kr.proten.pms.maintenance.service.entity.SiteChannel;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.person.PersonRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 유지보수 이슈 쓰기 (AC D3-1·D3-2·D3-3).
 *
 * <p>권한 판정이 없는 것이 이 테스트의 전제다 — US-D3은 로그인 사용자 전체이고,
 * {@link MaintenanceWriteAuthorizationTest}가 잠그는 네 계약 경로에 이슈는 들어가지
 * 않는다. 화자 id는 판정이 아니라 <b>기록</b>(감사 행위자·코멘트 작성자)에만 쓰인다.
 *
 * <p>시계를 고정한다 — 접수일·완료일이 "오늘"이라 실시간 시계로는 자정에 깨지는
 * 종류의 단정이 된다({@code ClockConfig}가 빈으로 둔 이유가 이것이다).
 */
@ExtendWith(MockitoExtension.class)
class IssueCommandTest {
    private static final long CALLER_ID = 7L;
    private static final long ENGINEER_ID = 26L;
    private static final long OTHER_PERSON_ID = 31L;
    private static final long CONTRACT_ID = 101L;
    private static final long SITE_ID = 55L;
    private static final long ISSUE_ID = 497L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);

    @Mock
    private MaintenanceIssueRepository issueRepository;
    @Mock
    private MaintenanceSiteRepository siteRepository;
    @Mock
    private IssueCommentRepository commentRepository;
    @Mock
    private IssueQueryService queryService;
    @Mock
    private MaintenanceViewFactory viewFactory;
    @Mock
    private PersonDirectoryService personDirectoryService;
    @Mock
    private MaintenanceAuditRecorder auditRecorder;
    @Mock
    private ApplicationEventPublisher events;

    private IssueCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(personDirectoryService.existsActive(anyLong())).thenReturn(true);
        lenient().when(issueRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // 처리 경로는 saveAndFlush로 저장한다 — 응답의 version이 커밋 뒤 값이어야 하고,
        // 그 사실은 통합 테스트가 실측으로 잡는다(단위에서는 반환만 이어 준다)
        lenient().when(issueRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(queryService.getIssue(anyLong())).thenReturn(view());
        service = new IssueCommandServiceImpl(
                issueRepository,
                siteRepository,
                commentRepository,
                queryService,
                viewFactory,
                personDirectoryService,
                auditRecorder,
                Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                        ZoneId.systemDefault()),
                events);
    }

    @Test
    @DisplayName("D3-1 — 입력 오류가 참조 오류보다 앞선다 (없는 사이트 + 빈 제목이면 400)")
    void inputErrorsPrecedeReferenceErrors() {
        // Given — 사이트도 없고 제목도 비었다. 순서가 뒤집히면 422가 나온다
        // When · Then
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.register(
                        CALLER_ID, new IssueCommand(999L, IssueType.INCIDENT, " ")))
                .satisfies(thrown -> assertThat(thrown.field()).isEqualTo("title"));
        // 참조 조회까지 가지 않았다 — 계약 쓰기와 같은 순서(400 → 422)
        verify(siteRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("D3-1 — 등록한 이슈는 max(id)+1 · 접수 · 오늘 접수 · 담당자는 사이트 엔지니어")
    void registerTakesNextIdAndDefaultsFromSite() {
        // Given
        when(siteRepository.findById(SITE_ID)).thenReturn(Optional.of(site(ENGINEER_ID)));
        when(issueRepository.nextId()).thenReturn(ISSUE_ID);

        // When
        service.register(CALLER_ID, command());

        // Then
        MaintenanceIssue saved = captureSaved();
        assertThat(saved.getId()).isEqualTo(ISSUE_ID);
        assertThat(saved.getStatus()).isEqualTo(IssueStatus.RECEIVED);
        assertThat(saved.getReceivedAt()).isEqualTo(TODAY);
        assertThat(saved.getCompletedAt()).isNull();
        assertThat(saved.getAssigneeId()).isEqualTo(ENGINEER_ID);
        assertThat(saved.getSiteId()).isEqualTo(SITE_ID);
        verify(auditRecorder).issueCreated(eq(CALLER_ID), any());
    }

    @Test
    @DisplayName("D3-1 — 제목의 앞뒤 공백은 다듬어 저장한다")
    void registerTrimsTitle() {
        // Given
        when(siteRepository.findById(SITE_ID)).thenReturn(Optional.of(site(ENGINEER_ID)));
        when(issueRepository.nextId()).thenReturn(ISSUE_ID);

        // When
        service.register(CALLER_ID, new IssueCommand(SITE_ID, IssueType.INCIDENT,
                "  로그인 지연  "));

        // Then
        assertThat(captureSaved().getTitle()).isEqualTo("로그인 지연");
    }

    @Test
    @DisplayName("D3-1 — 담당 엔지니어가 없는 사이트의 이슈는 미배정으로 남는다")
    void registerLeavesIssueUnassignedWhenSiteHasNoEngineer() {
        // Given
        when(siteRepository.findById(SITE_ID)).thenReturn(Optional.of(site(null)));
        when(issueRepository.nextId()).thenReturn(ISSUE_ID);

        // When
        service.register(CALLER_ID, command());

        // Then — D3-4 미배정 필터가 찾는 상태다
        assertThat(captureSaved().getAssigneeId()).isNull();
    }

    @Test
    @DisplayName("D3-1 — 사이트를 안 주면 400, 없는 사이트를 주면 422 REF_NOT_FOUND")
    void registerSeparatesMissingSiteFromUnknownSite() {
        // Given
        when(siteRepository.findById(999L)).thenReturn(Optional.empty());

        // When · Then — 지정이 없는 것과 지정이 틀린 것은 다른 오류다
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
                service.register(CALLER_ID, new IssueCommand(null, IssueType.INCIDENT, "제목")));
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.register(
                        CALLER_ID, new IssueCommand(999L, IssueType.INCIDENT, "제목")))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.REF_NOT_FOUND));
        verify(issueRepository, never()).save(any());
    }

    @Test
    @DisplayName("D3-1 — 유형·제목은 필수다")
    void registerRejectsMissingTypeOrTitle() {
        // Given
        lenient().when(siteRepository.findById(SITE_ID))
                .thenReturn(Optional.of(site(ENGINEER_ID)));

        // When · Then
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
                service.register(CALLER_ID, new IssueCommand(SITE_ID, null, "제목")));
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
                service.register(CALLER_ID, new IssueCommand(SITE_ID, IssueType.INCIDENT, " ")));
        verify(issueRepository, never()).save(any());
    }

    @Test
    @DisplayName("D3-1 — 등록은 MaintenanceIssueRegistered를 발행한다 (§8)")
    void registerPublishesEvent() {
        // Given
        when(siteRepository.findById(SITE_ID)).thenReturn(Optional.of(site(ENGINEER_ID)));
        when(issueRepository.nextId()).thenReturn(ISSUE_ID);

        // When
        service.register(CALLER_ID, command());

        // Then — 문구 재료를 실어 보낸다(구독자가 되물으면 반대 간선이 생긴다)
        ArgumentCaptor<MaintenanceIssueRegistered> event =
                ArgumentCaptor.forClass(MaintenanceIssueRegistered.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().issueId()).isEqualTo(ISSUE_ID);
        assertThat(event.getValue().assigneeId()).isEqualTo(ENGINEER_ID);
        assertThat(event.getValue().title()).isEqualTo("로그인 지연");
        assertThat(event.getValue().siteName()).isEqualTo("가천대길병원");
    }

    @Test
    @DisplayName("D3-1 — 담당자가 없어도 발행한다 (알릴 사람이 없다는 판단은 구독자 몫)")
    void registerPublishesEvenWithoutAssignee() {
        // Given
        when(siteRepository.findById(SITE_ID)).thenReturn(Optional.of(site(null)));
        when(issueRepository.nextId()).thenReturn(ISSUE_ID);

        // When
        service.register(CALLER_ID, command());

        // Then
        ArgumentCaptor<MaintenanceIssueRegistered> event =
                ArgumentCaptor.forClass(MaintenanceIssueRegistered.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().assigneeId()).isNull();
    }

    @Test
    @DisplayName("D3-2 — 접수 → 처리중 → 고객확인대기 → 완료가 순방향이고 완료일이 남는다")
    void processWalksTheForwardFlowAndStampsCompletion() {
        // Given
        MaintenanceIssue issue = issue(IssueStatus.RECEIVED);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        // When
        service.process(CALLER_ID, ISSUE_ID, status(IssueStatus.IN_PROGRESS), 0L);
        service.process(CALLER_ID, ISSUE_ID, status(IssueStatus.AWAITING_CLIENT), 0L);
        service.process(CALLER_ID, ISSUE_ID, status(IssueStatus.DONE), 0L);

        // Then
        assertThat(issue.getStatus()).isEqualTo(IssueStatus.DONE);
        assertThat(issue.getCompletedAt()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("D3-2 — 고객확인대기는 선택이라 처리중에서 완료로 바로 갈 수 있다")
    void processMaySkipAwaitingClient() {
        // Given
        MaintenanceIssue issue = issue(IssueStatus.IN_PROGRESS);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        // When
        service.process(CALLER_ID, ISSUE_ID, status(IssueStatus.DONE), 0L);

        // Then
        assertThat(issue.getStatus()).isEqualTo(IssueStatus.DONE);
    }

    @Test
    @DisplayName("D3-2 — 재개(완료 → 처리중)는 완료일을 지운다")
    void reopeningClearsCompletionDate() {
        // Given
        MaintenanceIssue issue = issue(IssueStatus.DONE);
        ReflectionTestUtils.setField(issue, "completedAt", TODAY.minusDays(3));
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        // When
        service.process(CALLER_ID, ISSUE_ID, status(IssueStatus.IN_PROGRESS), 0L);

        // Then — 재개된 이슈의 완료일은 사실이 아니다
        assertThat(issue.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
        assertThat(issue.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("D3-2 — 흐름에 없는 전이는 409 INVALID_TRANSITION이고 아무것도 안 바뀐다")
    void processRejectsTransitionsOutsideTheFlow() {
        // Given — 접수에서 완료로 건너뛰기
        MaintenanceIssue issue = issue(IssueStatus.RECEIVED);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() ->
                        service.process(CALLER_ID, ISSUE_ID, status(IssueStatus.DONE), 0L))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_TRANSITION));
        assertThat(issue.getStatus()).isEqualTo(IssueStatus.RECEIVED);
        verify(issueRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("D3-2 — 담당자만 바꾸는 요청은 상태를 건드리지 않는다 (PATCH 의미론)")
    void processChangesOnlyWhatWasSent() {
        // Given
        MaintenanceIssue issue = issue(IssueStatus.IN_PROGRESS);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        // When
        service.process(CALLER_ID, ISSUE_ID, new IssueEditCommand(null, OTHER_PERSON_ID), 0L);

        // Then
        assertThat(issue.getAssigneeId()).isEqualTo(OTHER_PERSON_ID);
        assertThat(issue.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("D3-2 — 상태만 바꾸는 요청은 담당자를 비우지 않는다")
    void processKeepsAssigneeWhenNotSent() {
        // Given
        MaintenanceIssue issue = issue(IssueStatus.RECEIVED);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        // When
        service.process(CALLER_ID, ISSUE_ID, status(IssueStatus.IN_PROGRESS), 0L);

        // Then — null은 "해제"가 아니라 "그대로"다
        assertThat(issue.getAssigneeId()).isEqualTo(ENGINEER_ID);
    }

    @Test
    @DisplayName("D3-2 — version이 어긋나면 409이고 참조 검증까지 가지 않는다")
    void processRejectsStaleVersionBeforeValidating() {
        // Given
        MaintenanceIssue issue = issue(IssueStatus.RECEIVED);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));

        // When · Then
        assertThatExceptionOfType(StaleVersionException.class).isThrownBy(() ->
                service.process(CALLER_ID, ISSUE_ID, status(IssueStatus.IN_PROGRESS), 4L));
        assertThat(issue.getStatus()).isEqualTo(IssueStatus.RECEIVED);
        verify(personDirectoryService, never()).existsActive(anyLong());
    }

    @Test
    @DisplayName("D3-2 — 없는 인원으로 재배정하면 422 REF_NOT_FOUND")
    void processRejectsUnknownAssignee() {
        // Given
        MaintenanceIssue issue = issue(IssueStatus.RECEIVED);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(personDirectoryService.existsActive(999L)).thenReturn(false);

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.process(
                        CALLER_ID, ISSUE_ID, new IssueEditCommand(null, 999L), 0L))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.REF_NOT_FOUND));
        assertThat(issue.getAssigneeId()).isEqualTo(ENGINEER_ID);
    }

    @Test
    @DisplayName("D3-2 — 스냅샷은 바꾸기 직전에 뜬다 (감사에 바뀐 필드만 남는다)")
    void processSnapshotsBeforeTheChange() {
        // Given
        MaintenanceIssue issue = issue(IssueStatus.RECEIVED);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(auditRecorder.snapshot(issue))
                .thenAnswer(invocation -> Map.of("status", issue.getStatus()));

        // When
        service.process(CALLER_ID, ISSUE_ID, status(IssueStatus.IN_PROGRESS), 0L);

        // Then
        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.captor();
        verify(auditRecorder).issueChanged(eq(CALLER_ID), eq(issue), before.capture());
        assertThat(before.getValue()).containsEntry("status", IssueStatus.RECEIVED);
    }

    @Test
    @DisplayName("D3-2 — 없는 이슈는 404다")
    void processRejectsUnknownIssue() {
        // Given
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.empty());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() ->
                service.process(CALLER_ID, ISSUE_ID, status(IssueStatus.IN_PROGRESS), 0L));
    }

    @Test
    @DisplayName("D3-3 — 코멘트는 화자를 작성자로 남기고 감사 행은 만들지 않는다")
    void addCommentRecordsAuthorWithoutAuditRow() {
        // Given
        when(issueRepository.existsById(ISSUE_ID)).thenReturn(true);
        when(commentRepository.save(any())).thenAnswer(invocation -> {
            IssueComment comment = invocation.getArgument(0);
            ReflectionTestUtils.setField(comment, "id", 1L);

            return comment;
        });
        when(viewFactory.refsOf(List.of(CALLER_ID)))
                .thenReturn(Map.of(CALLER_ID, personRef()));

        // When
        CommentView created = service.addComment(CALLER_ID, ISSUE_ID, "  현장 확인 완료  ");

        // Then — 코멘트 자체가 불변 기록이라 감사에 또 남기지 않는다
        assertThat(created.content()).isEqualTo("현장 확인 완료");
        assertThat(created.author().id()).isEqualTo(CALLER_ID);
        ArgumentCaptor<IssueComment> saved = ArgumentCaptor.forClass(IssueComment.class);
        verify(commentRepository).save(saved.capture());
        assertThat(saved.getValue().getAuthorId()).isEqualTo(CALLER_ID);
        assertThat(saved.getValue().getIssueId()).isEqualTo(ISSUE_ID);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(
                TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant());
        verify(auditRecorder, never()).issueChanged(anyLong(), any(), any());
    }

    @Test
    @DisplayName("D3-3 — 없는 이슈에는 코멘트를 달 수 없고, 빈 내용은 400이다")
    void addCommentRejectsUnknownIssueAndBlankContent() {
        // Given
        when(issueRepository.existsById(ISSUE_ID)).thenReturn(false, true);

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.addComment(CALLER_ID, ISSUE_ID, "내용"));
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.addComment(CALLER_ID, ISSUE_ID, " "));
        verify(commentRepository, never()).save(any());
    }

    private MaintenanceIssue captureSaved() {
        ArgumentCaptor<MaintenanceIssue> saved = ArgumentCaptor.forClass(MaintenanceIssue.class);
        verify(issueRepository).save(saved.capture());

        return saved.getValue();
    }

    private static IssueCommand command() {
        return new IssueCommand(SITE_ID, IssueType.INCIDENT, "로그인 지연");
    }

    private static IssueEditCommand status(IssueStatus status) {
        return new IssueEditCommand(status, null);
    }

    /** 사이트 id는 DB가 만든다(IDENTITY) — 단위 테스트에서는 주입해 준다. */
    private static MaintenanceSite site(Long engineerId) {
        MaintenanceSite site =
                MaintenanceSite.of(CONTRACT_ID, "가천대길병원", SiteChannel.OEM, null, engineerId);
        ReflectionTestUtils.setField(site, "id", SITE_ID);

        return site;
    }

    private static MaintenanceIssue issue(IssueStatus status) {
        return MaintenanceIssue.of(new IssueProfile(ISSUE_ID, SITE_ID, IssueType.INCIDENT,
                "로그인 지연", status, ENGINEER_ID, TODAY.minusDays(5), null));
    }

    private static IssueView view() {
        return new IssueView(ISSUE_ID, "장애", "접수", IssueStatus.RECEIVED, "로그인 지연",
                TODAY, null, personRef(),
                SITE_ID, "가천대길병원", CONTRACT_ID, "그룹웨어 유지보수", List.of(), 0L);
    }

    private static PersonRef personRef() {
        return new PersonRef(CALLER_ID, "박민수", "AI팀", "기술본부", "과장", true);
    }

}
