package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.audit.AuditAction;
import kr.proten.pms.audit.AuditQueryService;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.common.exception.StaleVersionException;
import kr.proten.pms.maintenance.repository.MaintenanceIssueRepository;
import kr.proten.pms.maintenance.service.ContractCommandService;
import kr.proten.pms.maintenance.service.IssueCommandService;
import kr.proten.pms.maintenance.service.IssueQueryService;
import kr.proten.pms.maintenance.service.dto.ContractCommand;
import kr.proten.pms.maintenance.service.dto.ContractDetail;
import kr.proten.pms.maintenance.service.dto.IssueCommand;
import kr.proten.pms.maintenance.service.dto.IssueEditCommand;
import kr.proten.pms.maintenance.service.dto.IssueQuery;
import kr.proten.pms.maintenance.service.dto.IssueView;
import kr.proten.pms.maintenance.service.dto.SiteCommand;
import kr.proten.pms.maintenance.service.dto.SiteView;
import kr.proten.pms.maintenance.service.entity.ContractStatus;
import kr.proten.pms.maintenance.service.entity.IssueStatus;
import kr.proten.pms.maintenance.service.entity.IssueType;
import kr.proten.pms.maintenance.service.entity.SiteChannel;
import kr.proten.pms.notification.service.NotificationService;
import kr.proten.pms.notification.service.dto.NotificationView;
import kr.proten.pms.notification.service.entity.NotificationType;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.OrgUnit;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 유지보수 이슈 쓰기 관통 (US-D3).
 *
 * <p>단위 테스트({@code IssueCommandTest})가 규칙을 이미 고정하므로 여기서 보는 것은
 * <b>실물에서만 드러나는 것</b>이다: {@code max(id)+1}이 진짜 다음 값을 내는지,
 * 등록한 이슈가 조회 경로(D3-4 목록·단건)에 나타나는지, 코멘트가 조회에 실리는지,
 * <b>한 유스케이스가 {@code @Version}을 한 번만 올리는지</b>, 그리고 이벤트가
 * maintenance → notification 두 모듈을 실제로 건너 알림이 되는지(§8).
 *
 * <p>전용 id 블록(9xx)과 전용 직급·조직 노드를 쓴다 — 공유 픽스처 행을 <b>바꾸지</b>
 * 않는 것이 규칙이다(2026-08-24 실측: 공유 행의 {@code @Version}이 오르면 같은 행을
 * 다시 저장하는 다른 통합 테스트가 낙관적 락으로 무너진다).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaintenanceIssueWritesIntegrationTest extends PostgresTestBase {
    private static final long MANAGER_GROUP_ID = 901L;
    private static final long MEMBER_GROUP_ID = 902L;

    private static final long MANAGER_ID = 901L;
    private static final long ENGINEER_ID = 902L;
    private static final long MEMBER_ID = 903L;

    private static final long TEAM_ID = 921L;
    private static final long GRADE_ID = 931L;

    @Autowired
    private OrgUnitRepository orgUnitRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private PermissionGroupRepository permissionGroupRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private MaintenanceIssueRepository issueRepository;
    @Autowired
    private ContractCommandService contractCommandService;
    @Autowired
    private IssueCommandService issueCommandService;
    @Autowired
    private IssueQueryService issueQueryService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private AuditQueryService auditQueryService;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        orgUnitRepository.save(OrgUnit.of(TEAM_ID, PersonFixtures.COMPANY_ID, "D3이슈팀"));
        gradeRepository.save(Grade.of(GRADE_ID, "D3선임", 1.0));
        permissionGroupRepository.saveAll(List.of(
                PersonFixtures.group(MANAGER_GROUP_ID, "D3계약관리", VisibilityScope.TEAM,
                        OrgPermission.MANAGE_CONTRACTS),
                PersonFixtures.group(MEMBER_GROUP_ID, "D3팀원", VisibilityScope.TEAM)));
        personRepository.saveAll(List.of(
                Person.of(MANAGER_ID, "D3계약담당", TEAM_ID, GRADE_ID, MANAGER_GROUP_ID, 1.0,
                        true, false, true),
                Person.of(ENGINEER_ID, "D3엔지니어", TEAM_ID, GRADE_ID, MEMBER_GROUP_ID, 1.0,
                        true, false, true),
                Person.of(MEMBER_ID, "D3팀원", TEAM_ID, GRADE_ID, MEMBER_GROUP_ID, 1.0,
                        true, false, true)));
    }

    @Test
    @DisplayName("D3-1 — 새 이슈 id는 max(id)+1이고 연속으로 발급된다")
    void newIssueIdsAreConsecutive() {
        // Given
        long siteId = siteOf("D3연속", ENGINEER_ID);

        // When
        IssueView first = register(siteId, "D3연속1");
        IssueView second = register(siteId, "D3연속2");

        // Then
        assertThat(second.id()).isEqualTo(first.id() + 1);
        assertThat(issueRepository.findById(second.id())).isPresent();
    }

    @Test
    @DisplayName("D3-1 — 팀원(계약 관리 플래그 없음)도 이슈를 등록할 수 있다")
    void anyLoggedInUserMayRegisterAnIssue() {
        // Given — 계약·사이트 쓰기는 403이던 사람이다(D2-3)
        long siteId = siteOf("D3권한", ENGINEER_ID);

        // When
        IssueView created = issueCommandService.register(
                MEMBER_ID, new IssueCommand(siteId, IssueType.INQUIRY, "D3팀원이 올린 문의"));

        // Then — US-D3은 로그인 사용자 전체다
        assertThat(created.id()).isPositive();
        assertThat(created.status()).isEqualTo(IssueStatus.RECEIVED.label());
    }

    @Test
    @DisplayName("D3-1 — 등록한 이슈가 목록(D3-4)과 단건 조회에 사이트·계약과 함께 나온다")
    void registeredIssueAppearsInTheReadPaths() {
        // Given
        long siteId = siteOf("D3조회", ENGINEER_ID);

        // When
        IssueView created = register(siteId, "D3조회대상");

        // Then — 담당자는 사이트의 엔지니어이고 참조로 나온다
        IssueView fetched = issueQueryService.getIssue(created.id());
        assertThat(fetched.title()).isEqualTo("D3조회대상");
        assertThat(fetched.assignee().name()).isEqualTo("D3엔지니어");
        assertThat(fetched.siteName()).isEqualTo("가천대길병원");
        assertThat(fetched.contractName()).isEqualTo("D3조회");
        assertThat(issuesOf(new IssueQuery(null, null, siteId, null, false, null)))
                .contains(created.id());
    }

    @Test
    @DisplayName("D3-1 — 담당 엔지니어가 없는 사이트의 이슈는 미배정 필터에 잡힌다")
    void issueOnASiteWithoutAnEngineerIsUnassigned() {
        // Given
        long siteId = siteOf("D3미배정", null);

        // When
        IssueView created = register(siteId, "D3미배정대상");

        // Then
        assertThat(created.assignee()).isNull();
        assertThat(issuesOf(new IssueQuery(null, null, siteId, null, true, null)))
                .contains(created.id());
    }

    @Test
    @DisplayName("D3-1 — 등록은 담당자에게 알림을 만든다 (§8 → F1-1 적재 경로)")
    void registeringNotifiesTheAssignee() {
        // Given
        long siteId = siteOf("D3알림", ENGINEER_ID);

        // When
        IssueView created = register(siteId, "D3알림대상");

        // Then — 커밋 후 비동기라 기다린다. maintenance → notification 두 모듈을 건넌다
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationsOf(ENGINEER_ID))
                        .anySatisfy(view -> {
                            assertThat(view.type()).isEqualTo(NotificationType.ISSUE_ASSIGNED);
                            assertThat(view.refType()).isEqualTo("MaintenanceIssue");
                            assertThat(view.refId()).isEqualTo(created.id());
                            assertThat(view.message()).contains("가천대길병원", "D3알림대상");
                        }));
    }

    @Test
    @DisplayName("D3-1 — 미배정 이슈는 아무에게도 알리지 않는다 (등록자에게도 가지 않는다)")
    void unassignedIssueNotifiesNobody() {
        // Given
        long siteId = siteOf("D3무알림", null);
        int before = notificationsOf(MANAGER_ID).size();

        // When
        register(siteId, "D3무알림대상");

        // Then — 이벤트는 발행되지만 구독자가 수신자를 찾지 못한다
        assertThat(notificationsOf(MANAGER_ID)).hasSize(before);
    }

    @Test
    @DisplayName("D3-2 — 한 번 처리하면 version은 한 칸만 오르고 옛 version은 409다")
    void processBumpsVersionOnceAndRejectsStaleRetries() {
        // Given
        IssueView created = register(siteOf("D3낙관락", ENGINEER_ID), "D3낙관락대상");
        assertThat(created.version()).isZero();

        // When
        IssueView updated = issueCommandService.process(
                MANAGER_ID, created.id(),
                new IssueEditCommand(IssueStatus.IN_PROGRESS, null), created.version());

        // Then — 두 칸 오르면 더러워진 세션에 질의한 것이다(conventions §4의 실측 사고)
        assertThat(updated.version()).isEqualTo(1L);
        assertThat(updated.status()).isEqualTo(IssueStatus.IN_PROGRESS.label());
        assertThatExceptionOfType(StaleVersionException.class)
                .isThrownBy(() -> issueCommandService.process(
                        MANAGER_ID, created.id(),
                        new IssueEditCommand(IssueStatus.DONE, null), created.version()));
    }

    @Test
    @DisplayName("D3-2 — 완료까지 처리하면 완료일이 남고, 재개하면 지워진다")
    void completionDateFollowsTheStatus() {
        // Given
        IssueView created = register(siteOf("D3완료", ENGINEER_ID), "D3완료대상");
        IssueView inProgress = issueCommandService.process(MANAGER_ID, created.id(),
                new IssueEditCommand(IssueStatus.IN_PROGRESS, null), created.version());

        // When
        IssueView done = issueCommandService.process(MANAGER_ID, created.id(),
                new IssueEditCommand(IssueStatus.DONE, null), inProgress.version());

        // Then
        assertThat(done.completedAt()).isEqualTo(LocalDate.now());

        // When — 재개
        IssueView reopened = issueCommandService.process(MANAGER_ID, created.id(),
                new IssueEditCommand(IssueStatus.IN_PROGRESS, null), done.version());

        // Then
        assertThat(reopened.completedAt()).isNull();
    }

    @Test
    @DisplayName("D3-2 — 재배정은 담당자를 바꾸고 감사 행에 두 값이 남는다")
    void reassignmentIsRecorded() {
        // Given
        IssueView created = register(siteOf("D3재배정", ENGINEER_ID), "D3재배정대상");

        // When
        IssueView reassigned = issueCommandService.process(MANAGER_ID, created.id(),
                new IssueEditCommand(null, MEMBER_ID), created.version());

        // Then
        assertThat(reassigned.assignee().name()).isEqualTo("D3팀원");
        AuditRecord row = auditOf(created.id(), AuditAction.UPDATE);
        assertThat(row.actorId()).isEqualTo(MANAGER_ID);
        // 스냅샷은 JSON을 왕복하므로 숫자가 Long으로 돌아오지 않는다(실측 2026-08-24) —
        // id를 감사에 담는 자리는 값을 숫자로 읽어야 한다
        assertThat(idAt(row.before(), "assigneeId")).isEqualTo(ENGINEER_ID);
        assertThat(idAt(row.after(), "assigneeId")).isEqualTo(MEMBER_ID);
        assertThat(row.projectId()).isNull();
    }

    @Test
    @DisplayName("D3-1 — 등록도 감사 행을 남긴다 (CREATE)")
    void registrationIsRecorded() {
        // Given
        IssueView created = register(siteOf("D3감사", ENGINEER_ID), "D3감사대상");

        // Then
        AuditRecord row = auditOf(created.id(), AuditAction.CREATE);
        assertThat(row.actorId()).isEqualTo(MANAGER_ID);
        assertThat(row.after()).containsEntry("title", "D3감사대상");
        assertThat(row.projectId()).isNull();
    }

    @Test
    @DisplayName("D3-3 — 코멘트는 이슈 조회에 시간순으로 실리고 이슈 version을 올리지 않는다")
    void commentsLandOnTheIssueWithoutTouchingItsVersion() {
        // Given
        IssueView created = register(siteOf("D3코멘트", ENGINEER_ID), "D3코멘트대상");

        // When
        issueCommandService.addComment(ENGINEER_ID, created.id(), "현장 도착");
        issueCommandService.addComment(MANAGER_ID, created.id(), "원인 확인 — 디스크 용량");

        // Then
        IssueView fetched = issueQueryService.getIssue(created.id());
        assertThat(fetched.comments())
                .extracting(comment -> comment.content())
                .containsExactly("현장 도착", "원인 확인 — 디스크 용량");
        assertThat(fetched.comments())
                .extracting(comment -> comment.author().name())
                .containsExactly("D3엔지니어", "D3계약담당");
        // 코멘트를 더하는 것은 이슈를 고치는 것이 아니다 — 낙관적 락을 건드리지 않는다
        assertThat(fetched.version()).isZero();
    }

    @Test
    @DisplayName("D3-3 — 코멘트는 감사 행을 만들지 않는다 (자기가 이미 불변 기록이다)")
    void commentsLeaveNoAuditRow() {
        // Given
        IssueView created = register(siteOf("D3코멘트감사", ENGINEER_ID), "D3코멘트감사대상");
        long before = auditRows(created.id()).size();

        // When
        issueCommandService.addComment(ENGINEER_ID, created.id(), "코멘트만 남긴다");

        // Then
        assertThat(auditRows(created.id())).hasSize((int) before);
    }

    /** 계약과 사이트를 하나씩 세우고 사이트 id를 준다 — 이슈는 사이트에 붙는다. */
    private long siteOf(String contractName, Long engineerId) {
        ContractDetail contract = contractCommandService.create(MANAGER_ID, new ContractCommand(
                "㈜가온아이", contractName, ContractStatus.ACTIVE, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 61320000L, 5110000L,
                MANAGER_ID, "검색엔진", "그룹웨어", "월 1회", null));
        SiteView site = contractCommandService.addSite(MANAGER_ID, contract.id(),
                new SiteCommand("가천대길병원", SiteChannel.OEM, null, engineerId, List.of()));

        return site.id();
    }

    private IssueView register(long siteId, String title) {
        return issueCommandService.register(
                MANAGER_ID, new IssueCommand(siteId, IssueType.INCIDENT, title));
    }

    private List<Long> issuesOf(IssueQuery query) {
        return issueQueryService.search(query, PageRequest.of(0, 100)).stream()
                .map(IssueView::id)
                .toList();
    }

    private List<NotificationView> notificationsOf(long personId) {
        return notificationService.listMine(personId, null, PageRequest.of(0, 100))
                .getContent();
    }

    private AuditRecord auditOf(long issueId, AuditAction action) {
        return auditRows(issueId).stream()
                .filter(record -> record.action() == action)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "이슈 " + issueId + "의 " + action + " 감사 행이 없다"));
    }

    private List<AuditRecord> auditRows(long issueId) {
        return auditQueryService.findAll(page()).stream()
                .filter(record -> "MaintenanceIssue".equals(record.entityType()))
                .filter(record -> record.entityId() != null && record.entityId() == issueId)
                .toList();
    }

    /** 감사 스냅샷의 id 값 — JSON 왕복 뒤에는 {@code Integer}일 수 있다. */
    private static long idAt(java.util.Map<String, Object> snapshot, String field) {
        return ((Number) snapshot.get(field)).longValue();
    }

    private static Pageable page() {
        return PageRequest.of(0, 500);
    }
}
