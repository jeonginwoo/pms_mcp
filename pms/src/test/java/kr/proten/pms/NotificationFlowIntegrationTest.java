package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.notification.service.NotificationService;
import kr.proten.pms.notification.service.dto.NotificationView;
import kr.proten.pms.notification.service.dto.NotifyCommand;
import kr.proten.pms.notification.service.entity.NotificationType;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.VisibilityScope;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.service.AssignmentService;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.ProjectLifecycleService;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.CreateAssignmentCommand;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * 알림 관통 (AC F1-1·F1-2·F1-3 · §8 이벤트 배관).
 *
 * <p>여기서만 보이는 것이 <b>이벤트가 실제로 건너간다</b>는 사실이다: 배정을 만들면
 * project가 발행하고 → resource가 과부하를 판정해 다시 발행하고 → notification이
 * 적재한다. 단위 테스트로는 세 모듈이 이어지는지 알 수 없다.
 *
 * <p>{@code @ApplicationModuleListener}는 <b>커밋 후 비동기</b>라 단정에 대기가 필요하다 —
 * 즉시 확인하면 아직 안 왔을 뿐인데 실패한다.
 *
 * <p>공유 컨테이너를 쓰므로 전용 id 블록(7xx)을 쓰고, 공유 픽스처 행은 바꾸지 않는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationFlowIntegrationTest extends PostgresTestBase {
    private static final long LEAD_GROUP_ID = 703L;
    private static final long MEMBER_GROUP_ID = 704L;

    /** 같은 조직의 "프로젝트 생성" 보유자 — 과부하 알림 수신자다 (F1-1). */
    private static final long LEAD_ID = 701L;
    /** 과부하가 될 사람 — 가용 1.0에 배정 1.5를 준다. */
    private static final long BUSY_ID = 702L;
    /** 같은 조직의 팀원 — 플래그가 없어 수신자가 아니다. */
    private static final long PLAIN_ID = 703L;

    private static final long TEAM_ID = 721L;
    private static final long GRADE_ID = 731L;

    @Autowired
    private OrgUnitRepository orgUnitRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private PermissionGroupRepository permissionGroupRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private ProjectCommandService projectCommandService;
    @Autowired
    private ProjectQueryService projectQueryService;
    @Autowired
    private ProjectLifecycleService projectLifecycleService;
    @Autowired
    private AssignmentService assignmentService;
    @Autowired
    private NotificationService notificationService;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        orgUnitRepository.save(kr.proten.pms.person.service.entity.OrgUnit.of(
                TEAM_ID, PersonFixtures.COMPANY_ID, "F알림팀"));
        // 전용 직급을 쓴다. PersonFixtures.person은 gradeId=1을 박아 두는데 그 행은
        // 다른 통합 테스트도 저장하므로, 여기서 건드리면 @Version이 올라가 그쪽이
        // 낙관적 락으로 죽는다(2026-08-24 실측 — 개명 테스트와 같은 계열의 오염)
        gradeRepository.save(Grade.of(GRADE_ID, "F선임", 1.0));
        permissionGroupRepository.saveAll(List.of(
                PersonFixtures.group(LEAD_GROUP_ID, "F팀장", VisibilityScope.TEAM,
                        OrgPermission.CREATE_PROJECT),
                PersonFixtures.group(MEMBER_GROUP_ID, "F팀원", VisibilityScope.TEAM)));
        // 가용 1.0 · billable — 과부하 산식이 성립하는 최소 조건이다
        personRepository.saveAll(List.of(
                Person.of(LEAD_ID, "F팀장", TEAM_ID, GRADE_ID, LEAD_GROUP_ID, 1.0, true, false, true),
                Person.of(BUSY_ID, "F과부하", TEAM_ID, GRADE_ID, MEMBER_GROUP_ID, 1.0, true, false, true),
                Person.of(PLAIN_ID, "F동료", TEAM_ID, GRADE_ID, MEMBER_GROUP_ID, 1.0, true, false, true)));
    }

    @Test
    @DisplayName("F1-1 — 배정이 과부하를 만들면 같은 조직의 플래그 보유자에게만 알림이 간다")
    void overbookingNotifiesColleaguesWithTheFlag() {
        // Given: **진행중**까지 올린다 — 가동률 분자는 진행중 배정만 세므로(2026-08-10),
        //        계약대기 상태로 배정하면 과부하가 성립하지 않는다(실측으로 드러났다)
        ProjectDetail project = projectCommandService.create(LEAD_ID, new CreateProjectCommand(
                "(주)가온아이", "F 과부하 유발 구축", "검색엔진", Engagement.REMOTE, 2.0,
                LocalDate.now().withDayOfMonth(1), LocalDate.now().plusMonths(1),
                List.of(new AssignmentSpec(LEAD_ID, ProjectRole.PM, null, null, 0.1))));
        long projectId = project.id();
        long version = advance(projectId, project.version(), ProjectStatus.ORDER_CONFIRMED);
        advance(projectId, version, ProjectStatus.IN_PROGRESS);

        // When: 가용 1.0에 1.5를 배정한다 → 기본 150%
        assignmentService.assign(LEAD_ID, new CreateAssignmentCommand(
                projectId, BUSY_ID, ProjectRole.PARTICIPANT,
                LocalDate.now().withDayOfMonth(1), LocalDate.now().plusMonths(1), 1.5));

        // Then: 커밋 후 비동기라 기다린다 — project → resource → notification 세 모듈을 건넌다
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(typesOf(LEAD_ID)).contains(NotificationType.OVERBOOKED));

        // 플래그 없는 동료에게는 가지 않는다 — "팀장"을 플래그로 판정한 결과다
        assertThat(typesOf(PLAIN_ID)).doesNotContain(NotificationType.OVERBOOKED);
        // 배정 당사자에게는 배정 알림이 간다(§8 MemberAssignedToProject)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(typesOf(BUSY_ID)).contains(NotificationType.ASSIGNED));
    }

    @Test
    @DisplayName("F1-2 — 같은 dedupeKey는 두 번 적재되지 않는다")
    void notifyIsIdempotent() {
        NotifyCommand command = new NotifyCommand(PLAIN_ID, NotificationType.DEADLINE_NEAR,
                "Project", 1L, "마감이 다가옵니다", "deadline:1:2026-08");

        notificationService.notify(command);
        notificationService.notify(command);

        // 유형이 아니라 **이 사건**으로 센다 — 같은 유형을 쓰는 다른 테스트가 있으면
        // 유형 개수는 실행 순서에 따라 달라진다(실측으로 깨졌다)
        assertThat(notificationService.listMine(PLAIN_ID, null, PageRequest.of(0, 50))
                        .getContent())
                .filteredOn(view -> view.refId() != null && view.refId() == 1L)
                .hasSize(1);
    }

    @Test
    @DisplayName("F1-3 — 읽음 처리 후에는 미읽음 목록에서 빠진다")
    void markReadRemovesFromUnread() {
        notificationService.notify(new NotifyCommand(PLAIN_ID, NotificationType.PROJECT_COMPLETED,
                "Project", 2L, "완료되었습니다", "completed:2:703"));
        long id = unread(PLAIN_ID).stream()
                .filter(view -> view.type() == NotificationType.PROJECT_COMPLETED)
                .findFirst().orElseThrow().id();

        notificationService.markRead(PLAIN_ID, id);

        assertThat(unread(PLAIN_ID)).extracting(NotificationView::id).doesNotContain(id);
    }

    @Test
    @DisplayName("F1-3 — 남의 알림 읽음 처리는 404다 (403이면 존재가 드러난다)")
    void markingSomeoneElsesNotificationIsNotFound() {
        notificationService.notify(new NotifyCommand(LEAD_ID, NotificationType.DEADLINE_NEAR,
                "Project", 3L, "남의 알림", "deadline:3:701"));
        long id = unread(LEAD_ID).stream()
                .filter(view -> view.refId() != null && view.refId() == 3L)
                .findFirst().orElseThrow().id();

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> notificationService.markRead(PLAIN_ID, id));
    }

    @Test
    @DisplayName("F3-3 — 회수는 미읽음만 지운다 (읽은 것은 남는다)")
    void withdrawRemovesOnlyUnread() {
        notificationService.notify(new NotifyCommand(PLAIN_ID, NotificationType.COMPLETION_OVERDUE,
                "Project", 9L, "완료 지연", "overdue:9:703"));
        notificationService.notify(new NotifyCommand(LEAD_ID, NotificationType.COMPLETION_OVERDUE,
                "Project", 9L, "완료 지연", "overdue:9:701"));
        long readOne = unread(LEAD_ID).stream()
                .filter(view -> view.type() == NotificationType.COMPLETION_OVERDUE)
                .findFirst().orElseThrow().id();
        notificationService.markRead(LEAD_ID, readOne);

        int withdrawn = notificationService.withdrawUnread(
                "Project", 9L, NotificationType.COMPLETION_OVERDUE);

        // 미읽음 1건만 사라지고, 이미 읽은 1건은 남는다
        assertThat(withdrawn).isEqualTo(1);
        assertThat(typesOf(LEAD_ID)).contains(NotificationType.COMPLETION_OVERDUE);
        assertThat(typesOf(PLAIN_ID)).doesNotContain(NotificationType.COMPLETION_OVERDUE);
    }

    @Test
    @DisplayName("F1-5·H1-4 — 껐으면 적재도 하지 않는다 (숨기는 것이 아니다)")
    void mutedTypeIsNotStoredAtAll() {
        // Given: 기본은 전부 켜짐이다 — 끈 것만 저장하는 opt-out이라 행이 없으면 켜진 것
        assertThat(notificationService.myPreferences(PLAIN_ID).enabled())
                .containsEntry(NotificationType.DEADLINE_NEAR, true);
        notificationService.updatePreferences(PLAIN_ID,
                Map.of(NotificationType.DEADLINE_NEAR, false));

        // When
        notificationService.notify(new NotifyCommand(PLAIN_ID, NotificationType.DEADLINE_NEAR,
                "Project", 42L, "마감", "deadline:42:703"));

        // Then: 목록에서 숨기는 것이 아니라 만들지 않는다 — 나중에 켜도 그 사이 것은 없다
        assertThat(typesOf(PLAIN_ID))
                .filteredOn(type -> type == NotificationType.DEADLINE_NEAR)
                .isEmpty();

        // 다시 켜면 들어온다. 전체 교체이므로 빠진 유형은 켜짐으로 돌아간다(PUT 의미론)
        notificationService.updatePreferences(PLAIN_ID, Map.of());
        assertThat(notificationService.myPreferences(PLAIN_ID).enabled())
                .containsEntry(NotificationType.DEADLINE_NEAR, true);
        notificationService.notify(new NotifyCommand(PLAIN_ID, NotificationType.DEADLINE_NEAR,
                "Project", 43L, "마감", "deadline:43:703"));
        assertThat(typesOf(PLAIN_ID)).contains(NotificationType.DEADLINE_NEAR);
    }

    @Test
    @DisplayName("H1-4 — 설정 응답은 언제나 유형 전체를 담는다 (화면이 토글을 그린다)")
    void preferencesAlwaysCoverEveryType() {
        assertThat(notificationService.myPreferences(LEAD_ID).enabled())
                .containsOnlyKeys(NotificationType.values());
    }

    /** §5는 한 칸씩만 전이한다 — 계약대기 → 수주확정 → 진행중. */
    @Test
    @DisplayName("§8 — 완료 처리는 배정 인원에게 안내를 보낸다 (2026-08-25까지 발행 0곳이었다)")
    void completingAProjectNotifiesItsMembers() {
        // Given: 진행중 · 100%까지 올린다
        long projectId = givenCompletable();

        // When
        ProjectDetail detail = projectQueryService.getProject(LEAD_ID, projectId);
        projectLifecycleService.complete(LEAD_ID, projectId, detail.version());

        // Then — 커밋 후 비동기다
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(typesOf(BUSY_ID)).contains(NotificationType.PROJECT_COMPLETED));
    }

    @Test
    @DisplayName("F3-3 — 재개하면 그 프로젝트의 미읽음 완료 지연 알림이 회수된다 (배선)")
    void reopeningWithdrawsOverdueNotifications() {
        // Given: 완료까지 갔다가 되돌릴 프로젝트 + 그 프로젝트의 완료 지연 알림
        long projectId = givenCompletable();
        ProjectDetail completable = projectQueryService.getProject(LEAD_ID, projectId);
        ProjectDetail completed =
                projectLifecycleService.complete(LEAD_ID, projectId, completable.version());
        notificationService.notify(new NotifyCommand(LEAD_ID,
                NotificationType.COMPLETION_OVERDUE, "Project", projectId, "완료 지연",
                "overdue:%d:%d".formatted(projectId, LEAD_ID)));
        assertThat(typesOf(LEAD_ID)).contains(NotificationType.COMPLETION_OVERDUE);

        // When
        projectLifecycleService.reopen(LEAD_ID, projectId, completed.version());

        // Then — 회수 메서드는 전부터 있었지만 이것을 부르는 자리가 없었다(2026-08-25 실측)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(unread(LEAD_ID))
                        .noneMatch(view -> view.type() == NotificationType.COMPLETION_OVERDUE
                                && view.refId() != null && view.refId() == projectId));
    }

    /** 진행중 · 100%까지 올린 프로젝트 — 완료 처리의 전제다(A7-1). */
    private long givenCompletable() {
        ProjectDetail project = projectCommandService.create(LEAD_ID, new CreateProjectCommand(
                "(주)가온아이", "F 완료대상 " + counter++, "검색엔진", Engagement.REMOTE, 1.0,
                LocalDate.now().withDayOfMonth(1), LocalDate.now().plusMonths(1),
                List.of(new AssignmentSpec(LEAD_ID, ProjectRole.PM, null, null, 0.1),
                        new AssignmentSpec(BUSY_ID, ProjectRole.PARTICIPANT, null, null, 0.1))));
        String name = project.name();
        long version = advance(project.id(), project.version(),
                ProjectStatus.ORDER_CONFIRMED, name);
        advance(project.id(), version, ProjectStatus.IN_PROGRESS, name);
        ProjectDetail inProgress = projectQueryService.getProject(LEAD_ID, project.id());
        projectLifecycleService.updateProgress(LEAD_ID, new UpdateProgressCommand(
                project.id(), 100, inProgress.version(), true));

        return project.id();
    }

    private int counter = 1;

    private long advance(long projectId, long version, ProjectStatus status) {
        return advance(projectId, version, status, "F 과부하 유발 구축");
    }

    /**
     * 이름을 보존하며 상태만 올린다 — {@code edit}은 전체 교체라 이름을 함께 보내야
     * 하고, 고정 이름을 쓰면 <b>호출한 쪽의 프로젝트가 개명된다</b>(2026-08-25 실측:
     * 그래서 두 테스트의 프로젝트 이름이 충돌해 409가 났다).
     */
    private long advance(long projectId, long version, ProjectStatus status, String name) {
        return projectCommandService.edit(LEAD_ID, new EditProjectCommand(
                projectId, "(주)가온아이", name, "검색엔진", Engagement.REMOTE,
                2.0, LocalDate.now().withDayOfMonth(1), LocalDate.now().plusMonths(1),
                status, version)).version();
    }

    private List<NotificationType> typesOf(long personId) {
        return notificationService.listMine(personId, null, PageRequest.of(0, 50))
                .getContent().stream().map(NotificationView::type).toList();
    }

    private List<NotificationView> unread(long personId) {
        return notificationService.listMine(personId, false, PageRequest.of(0, 50)).getContent();
    }
}
