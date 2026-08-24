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
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.maintenance.repository.MaintenanceContractRepository;
import kr.proten.pms.maintenance.service.MaintenanceQueryService;
import kr.proten.pms.maintenance.service.dto.ContractDetail;
import kr.proten.pms.notification.NotificationService;
import kr.proten.pms.notification.NotificationType;
import kr.proten.pms.notification.NotificationView;
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
import kr.proten.pms.project.HandoverSpec;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.ProjectLifecycleService;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.AssignmentSpec;
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
 * 유지보수 이관 관통 (US-D1) — 실물 PostgreSQL.
 *
 * <p>단위 테스트가 규칙과 순서를 이미 고정하므로 여기서 보는 것은 <b>한 트랜잭션이
 * 진짜 하나인지</b>다. D1-2·D1-3의 "아무것도 안 바뀜"은 목으로는 증명되지 않는다 —
 * 목은 저장을 부르지 않았다는 것만 말하고, 실물은 <b>DB에 행이 없다</b>는 것을 말한다.
 * 그 차이가 이 클래스의 존재 이유다: D1-2는 계약 행이 <b>DB에 없다</b>를, D1-3은
 * 계약이 없고 <b>프로젝트도 완료로 남았다</b>를 실물로 확인한다. 그 반대 방향
 * (계약이 저장된 뒤 전이가 실패해 계약이 사라지는 경로)은 현 순서상 검증이 전이보다
 * 앞이라 결정론적으로 재현할 수 없어 여기서 단정하지 않는다 — 그 보장은 어댑터가
 * 호출자 트랜잭션에 참여한다는 사실(@Transactional REQUIRED)에서 온다.
 *
 * <p>이관은 두 모듈을 건너므로 감사도 두 행이다(계약 CREATE · 프로젝트 STATE_CHANGE),
 * 그 둘이 같은 트랜잭션에 있는지도 여기서만 보인다.
 *
 * <p>전용 id 블록(7xx)과 전용 직급·조직 노드를 쓴다 — 공유 픽스처 행을 <b>바꾸지</b>
 * 않는 것이 규칙이다(2026-08-24 실측). 시연 앵커는 명화공업이다(부록 B).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaintenanceHandoverIntegrationTest extends PostgresTestBase {
    private static final long ADMIN_GROUP_ID = 701L;
    private static final long MEMBER_GROUP_ID = 702L;

    private static final long PM_ID = 701L;
    private static final long ENGINEER_ID = 702L;
    private static final long MEMBER_ID = 703L;

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
    private MaintenanceContractRepository contractRepository;
    @Autowired
    private ProjectCommandService projectCommandService;
    @Autowired
    private ProjectLifecycleService projectLifecycleService;
    @Autowired
    private ProjectQueryService projectQueryService;
    @Autowired
    private MaintenanceQueryService maintenanceQueryService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private AuditQueryService auditQueryService;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        orgUnitRepository.save(OrgUnit.of(TEAM_ID, PersonFixtures.COMPANY_ID, "D1이관팀"));
        gradeRepository.save(Grade.of(GRADE_ID, "D1선임", 1.0));
        permissionGroupRepository.saveAll(List.of(
                // MANAGE_CONTRACTS를 <b>일부러 주지 않는다</b> — 이관은 그 플래그 없이도
                // 되어야 한다(D1은 [PM]이고 어댑터가 ContractWriteGuard를 타지 않는다).
                // 여기에 플래그를 더하면 그 회귀 커버리지가 조용히 사라진다
                PersonFixtures.group(ADMIN_GROUP_ID, "D1관리자", VisibilityScope.COMPANY,
                        OrgPermission.CREATE_PROJECT),
                PersonFixtures.group(MEMBER_GROUP_ID, "D1팀원", VisibilityScope.COMPANY)));
        personRepository.saveAll(List.of(
                Person.of(PM_ID, "D1피엠", TEAM_ID, GRADE_ID, ADMIN_GROUP_ID, 1.0,
                        true, false, true),
                Person.of(ENGINEER_ID, "D1엔지니어", TEAM_ID, GRADE_ID, MEMBER_GROUP_ID, 1.0,
                        true, false, true),
                Person.of(MEMBER_ID, "D1팀원", TEAM_ID, GRADE_ID, MEMBER_GROUP_ID, 1.0,
                        true, false, true)));
    }

    @Test
    @DisplayName("D1-1 — 이관하면 상태가 유지보수중이 되고 계약이 조회 경로에 나온다")
    void handoverCreatesTheContractAndMovesTheProject() {
        // Given
        ProjectDetail completed = givenCompleted("D1이관대상");

        // When
        ProjectDetail handedOver = projectLifecycleService.handover(
                PM_ID, completed.id(), spec("명화공업 본사"), completed.version());

        // Then
        assertThat(handedOver.status()).isEqualTo(ProjectStatus.UNDER_MAINTENANCE);
        ContractDetail contract = contractOf(completed.id());
        assertThat(contract.name()).isEqualTo("MES 유지보수");
        assertThat(contract.contractor()).isEqualTo("명화공업");
        // 이관이 sourceProjectId를 채우는 유일한 입구다 — 직접 등록(D2-1)은 null이다
        assertThat(contract.sourceProjectId()).isEqualTo(completed.id());
        assertThat(contract.sites()).singleElement().satisfies(site -> {
            assertThat(site.name()).isEqualTo("명화공업 본사");
            assertThat(site.engineer().name()).isEqualTo("D1엔지니어");
        });
    }

    @Test
    @DisplayName("D1-1 — 이관은 감사 2행을 남긴다 (계약 CREATE · 프로젝트 STATE_CHANGE)")
    void handoverRecordsBothSides() {
        // Given
        ProjectDetail completed = givenCompleted("D1감사");

        // When
        projectLifecycleService.handover(
                PM_ID, completed.id(), spec("명화공업 감사"), completed.version());

        // Then
        long contractId = contractOf(completed.id()).id();
        AuditRecord contractRow = auditOf("MaintenanceContract", contractId);
        assertThat(contractRow.action()).isEqualTo(AuditAction.CREATE);
        assertThat(contractRow.actorId()).isEqualTo(PM_ID);
        // 계약은 프로젝트 스코프가 아니다 — 이관 계약도 그렇다(2026-08-25 판단 유지)
        assertThat(contractRow.projectId()).isNull();

        AuditRecord projectRow = auditOf("Project", completed.id());
        assertThat(projectRow.action()).isEqualTo(AuditAction.STATE_CHANGE);
        assertThat(projectRow.projectId()).isEqualTo(completed.id());
        // 이관 사실을 프로젝트별 이력(G2-2)에서 찾는 경로가 이 행이다
        assertThat(projectRow.after()).containsEntry("status", "UNDER_MAINTENANCE");
    }

    @Test
    @DisplayName("D1-2 — 완료가 아니면 409이고 계약 행이 DB에 생기지 않는다")
    void handoverOnUnfinishedProjectLeavesNoContract() {
        // Given — 진행중까지만 올린다
        ProjectDetail inProgress = advanceToInProgress(createProject("D1미완료"));
        long before = contractRepository.count();

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> projectLifecycleService.handover(
                        PM_ID, inProgress.id(), spec("명화공업 미완료"), inProgress.version()))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_TRANSITION));
        // 목이 아니라 실물 행 수다 — 이것이 D1-2의 "아무것도 안 바뀜"이다
        assertThat(contractRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("D1-3 — 없는 담당자로 이관하면 422이고 계약도 상태 전이도 없다 (원자성)")
    void handoverWithBadSpecRollsEverythingBack() {
        // Given
        ProjectDetail completed = givenCompleted("D1원자성");
        long before = contractRepository.count();
        HandoverSpec unknownEngineer = new HandoverSpec("명화공업", "MES 유지보수",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31), 24000000L, 2000000L,
                List.of(new HandoverSpec.Site("명화공업 본사", 999999L)));

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> projectLifecycleService.handover(
                        PM_ID, completed.id(), unknownEngineer, completed.version()))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.REF_NOT_FOUND));
        assertThat(contractRepository.count()).isEqualTo(before);
        assertThat(contractRepository.findBySourceProjectId(completed.id())).isEmpty();
        // D1-3 "상태 전이도 미발생" — 실물에서 프로젝트가 완료로 남았는지 본다
        // (목은 saveAndFlush를 안 불렀다까지만 말한다)
        assertThat(projectQueryService.getProject(PM_ID, completed.id()).status())
                .isEqualTo(ProjectStatus.COMPLETED);
    }

    @Test
    @DisplayName("D1-2 — 이관된 프로젝트는 다시 이관할 수 없고 재개도 안 된다")
    void handedOverProjectIsTerminal() {
        // Given
        ProjectDetail completed = givenCompleted("D1종착");
        ProjectDetail handedOver = projectLifecycleService.handover(
                PM_ID, completed.id(), spec("명화공업 종착"), completed.version());

        // When · Then — 두 번째 이관
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> projectLifecycleService.handover(
                        PM_ID, handedOver.id(), spec("명화공업 재이관"), handedOver.version()));
        // 재개도 막힌다 — 이관된 계약과의 정합을 보호하는 규칙이다(§5)
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> projectLifecycleService.reopen(
                        PM_ID, handedOver.id(), handedOver.version()));
    }

    @Test
    @DisplayName("D1-1 — 이관은 사이트 담당 엔지니어에게 알린다 (실행한 PM에게는 가지 않는다)")
    void handoverNotifiesTheSiteEngineer() {
        // Given
        ProjectDetail completed = givenCompleted("D1알림");

        // When
        projectLifecycleService.handover(
                PM_ID, completed.id(), spec("명화공업 알림"), completed.version());

        // Then — 커밋 후 비동기다. maintenance → notification 두 모듈을 건넌다
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationsOf(ENGINEER_ID)).anySatisfy(view -> {
                    assertThat(view.type()).isEqualTo(NotificationType.PROJECT_COMPLETED);
                    assertThat(view.refId()).isEqualTo(completed.id());
                    assertThat(view.message()).contains("D1피엠", "MES 유지보수");
                }));
        // 자기가 방금 한 일을 자기에게 알리지 않는다.
        // 총 건수로 세지 않는 이유(2026-08-25): 이관 알림은 완료 안내와 유형·대상이
        // 같으므로(PROJECT_COMPLETED · Project:id — 유형 재사용은 사용자 결정) 픽스처의
        // 완료 처리가 PM에게 남긴 안내와 섞인다. 구분되는 것은 문구다
        assertThat(notificationsOf(PM_ID))
                .noneMatch(view -> "Project".equals(view.refType())
                        && view.refId() != null && view.refId() == completed.id()
                        && view.message().contains("유지보수 담당으로 이관"));
    }

    @Test
    @DisplayName("D1-1 — \"계약 관리\" 플래그 없는 PM도 이관할 수 있다 (관문 재사용 금지)")
    void pmWithoutContractFlagMayHandOver() {
        // Given — D1관리자 그룹에는 MANAGE_CONTRACTS가 없다(픽스처 주석 참조).
        //         어댑터가 ContractCommandService를 재사용하면 이 테스트가 403으로 깨진다
        ProjectDetail completed = givenCompleted("D1무플래그");

        // When
        ProjectDetail handedOver = projectLifecycleService.handover(
                PM_ID, completed.id(), spec("명화공업 무플래그"), completed.version());

        // Then
        assertThat(handedOver.status()).isEqualTo(ProjectStatus.UNDER_MAINTENANCE);
        assertThat(contractRepository.findBySourceProjectId(completed.id())).isPresent();
    }

    /** 완료 상태까지 올린 프로젝트 — 이관의 유일한 전제다(§5). */
    private ProjectDetail givenCompleted(String name) {
        ProjectDetail inProgress = advanceToInProgress(createProject(name));
        var progressed = projectLifecycleService.updateProgress(PM_ID,
                new UpdateProgressCommand(
                        inProgress.id(), 100, inProgress.version(), true));

        return projectLifecycleService.complete(
                PM_ID, inProgress.id(), progressed.version());
    }

    private ProjectDetail createProject(String name) {
        return projectCommandService.create(PM_ID, new CreateProjectCommand(
                "명화공업", name, "검색엔진", Engagement.REMOTE, 2.0,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31),
                List.of(new AssignmentSpec(PM_ID, ProjectRole.PM, null, null, 0.5))));
    }

    private ProjectDetail advanceToInProgress(ProjectDetail created) {
        ProjectDetail confirmed = projectCommandService.edit(PM_ID, editCommand(
                created.id(), created.name(), ProjectStatus.ORDER_CONFIRMED, created.version()));

        return projectCommandService.edit(PM_ID, editCommand(
                created.id(), created.name(), ProjectStatus.IN_PROGRESS, confirmed.version()));
    }

    private EditProjectCommand editCommand(
            long projectId, String name, ProjectStatus status, long version) {
        return new EditProjectCommand(projectId, "명화공업", name, "검색엔진",
                Engagement.REMOTE, 2.0, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31), status, version);
    }

    private static HandoverSpec spec(String siteName) {
        return new HandoverSpec("명화공업", "MES 유지보수", LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31), 24000000L, 2000000L,
                List.of(new HandoverSpec.Site(siteName, ENGINEER_ID)));
    }

    private ContractDetail contractOf(long projectId) {
        return maintenanceQueryService.getContract(
                contractRepository.findBySourceProjectId(projectId)
                        .orElseThrow(() -> new AssertionError(
                                "프로젝트 " + projectId + "의 이관 계약이 없다"))
                        .getId());
    }

    private List<NotificationView> notificationsOf(long personId) {
        return notificationService.listMine(personId, null, PageRequest.of(0, 100))
                .getContent();
    }

    private AuditRecord auditOf(String entityType, long entityId) {
        return auditQueryService.findAll(PageRequest.of(0, 500)).stream()
                .filter(record -> entityType.equals(record.entityType()))
                .filter(record -> record.entityId() != null && record.entityId() == entityId)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        entityType + " " + entityId + " 감사 행이 없다"));
    }
}
