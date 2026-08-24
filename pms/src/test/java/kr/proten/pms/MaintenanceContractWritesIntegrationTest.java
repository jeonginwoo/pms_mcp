package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.audit.AuditAction;
import kr.proten.pms.audit.AuditQueryService;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.StaleVersionException;
import kr.proten.pms.maintenance.repository.MaintenanceContactRepository;
import kr.proten.pms.maintenance.repository.MaintenanceContractRepository;
import kr.proten.pms.maintenance.service.ContractCommandService;
import kr.proten.pms.maintenance.service.MaintenanceQueryService;
import kr.proten.pms.maintenance.service.dto.ContactCommand;
import kr.proten.pms.maintenance.service.dto.ContractCommand;
import kr.proten.pms.maintenance.service.dto.ContractDetail;
import kr.proten.pms.maintenance.service.dto.ContractQuery;
import kr.proten.pms.maintenance.service.dto.SiteCommand;
import kr.proten.pms.maintenance.service.dto.SiteView;
import kr.proten.pms.maintenance.service.entity.ContactParty;
import kr.proten.pms.maintenance.service.entity.ContractStatus;
import kr.proten.pms.maintenance.service.entity.SiteChannel;
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

/**
 * 유지보수 계약·사이트 쓰기 관통 (US-D2).
 *
 * <p>단위 테스트가 규칙을 이미 고정하므로 여기서 보는 것은 <b>실물에서만 드러나는
 * 것</b>이다: {@code max(id)+1}이 진짜 다음 값을 내는지, 등록한 계약이 조회 경로
 * (D4-1 keyword · D4-2 상세)에 나타나는지, 연락처 전체 교체가 DB에서 실제로
 * 일어나는지, 감사 행이 남는지, 그리고 <b>한 유스케이스가 {@code @Version}을 한 번만
 * 올리는지</b>(더러워진 세션에 질의하면 두 번 오른다 — conventions §4의 실측 사고).
 *
 * <p>전용 id 블록(8xx)과 전용 직급·조직 노드를 쓴다. 공유 픽스처 행을 <b>바꾸지</b>
 * 않는 것이 규칙이다(2026-08-24 실측 — 공유 행의 {@code @Version}이 오르면 같은 행을
 * 다시 저장하는 다른 통합 테스트가 낙관적 락으로 무너진다).
 *
 * <p>유지보수 시드는 이 컨텍스트에서 꺼져 있으므로({@code pms.seed.path} 공백)
 * 계약 표는 이 클래스가 만든 것만 담는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaintenanceContractWritesIntegrationTest extends PostgresTestBase {
    private static final long MANAGER_GROUP_ID = 801L;
    private static final long MEMBER_GROUP_ID = 802L;

    private static final long MANAGER_ID = 801L;
    private static final long ENGINEER_ID = 802L;
    private static final long MEMBER_ID = 803L;

    private static final long TEAM_ID = 821L;
    private static final long GRADE_ID = 831L;

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
    private MaintenanceContactRepository contactRepository;
    @Autowired
    private ContractCommandService contractCommandService;
    @Autowired
    private MaintenanceQueryService maintenanceQueryService;
    @Autowired
    private AuditQueryService auditQueryService;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        orgUnitRepository.save(OrgUnit.of(TEAM_ID, PersonFixtures.COMPANY_ID, "D계약팀"));
        // 전용 직급 — PersonFixtures.person이 박아 두는 gradeId=1 행은 다른 통합
        // 테스트도 저장하므로 건드리지 않는다(NotificationFlowIntegrationTest와 같은 이유)
        gradeRepository.save(Grade.of(GRADE_ID, "D선임", 1.0));
        permissionGroupRepository.saveAll(List.of(
                PersonFixtures.group(MANAGER_GROUP_ID, "D계약관리", VisibilityScope.TEAM,
                        OrgPermission.MANAGE_CONTRACTS),
                PersonFixtures.group(MEMBER_GROUP_ID, "D팀원", VisibilityScope.TEAM)));
        personRepository.saveAll(List.of(
                Person.of(MANAGER_ID, "D계약담당", TEAM_ID, GRADE_ID, MANAGER_GROUP_ID, 1.0,
                        true, false, true),
                Person.of(ENGINEER_ID, "D엔지니어", TEAM_ID, GRADE_ID, MEMBER_GROUP_ID, 1.0,
                        true, false, true),
                Person.of(MEMBER_ID, "D팀원", TEAM_ID, GRADE_ID, MEMBER_GROUP_ID, 1.0,
                        true, false, true)));
    }

    @Test
    @DisplayName("D2-1 — 새 계약 id는 max(id)+1이다 (연속으로 발급된다)")
    void newContractIdsAreConsecutive() {
        // When
        ContractDetail first = contractCommandService.create(MANAGER_ID, contract("D연속1"));
        ContractDetail second = contractCommandService.create(MANAGER_ID, contract("D연속2"));

        // Then
        assertThat(second.id()).isEqualTo(first.id() + 1);
        assertThat(contractRepository.findById(second.id())).isPresent();
    }

    @Test
    @DisplayName("D2-1 — 직접 등록한 계약이 계약 목록(D4-1)에 keyword로 잡힌다")
    void directlyRegisteredContractIsSearchable() {
        // Given
        ContractDetail created = contractCommandService.create(MANAGER_ID, contract("D검색대상"));

        // When
        ContractQuery query = new ContractQuery(null, null, null, "D검색대상");

        // Then
        assertThat(maintenanceQueryService.search(query, PageRequest.of(0, 10)))
                .extracting(summary -> summary.id())
                .containsExactly(created.id());
        assertThat(created.sourceProjectId()).isNull();
    }

    @Test
    @DisplayName("D2-4 — 사이트와 연락처가 계약 상세에 실린다 (raw는 조립본)")
    void siteAndContactsLandOnTheDetail() {
        // Given
        ContractDetail created = contractCommandService.create(MANAGER_ID, contract("D사이트"));

        // When
        contractCommandService.addSite(MANAGER_ID, created.id(), new SiteCommand(
                "가천대길병원", SiteChannel.OEM, "1번서버", ENGINEER_ID,
                List.of(new ContactCommand(ContactParty.CLIENT, "이준혁", "사원", "02-2140-5773",
                        "junhyuk@example.com"))));

        // Then
        ContractDetail detail = maintenanceQueryService.getContract(created.id());
        assertThat(detail.sites()).singleElement().satisfies(site -> {
            assertThat(site.name()).isEqualTo("가천대길병원");
            // 엔지니어는 id가 아니라 참조로 나온다 — 이름을 되묻지 않게 한다
            assertThat(site.engineer().name()).isEqualTo("D엔지니어");
            assertThat(site.contacts()).singleElement().satisfies(contact -> {
                assertThat(contact.name()).isEqualTo("이준혁");
                assertThat(contact.raw())
                        .isEqualTo("이준혁 사원 02-2140-5773 (junhyuk@example.com)");
            });
        });
    }

    @Test
    @DisplayName("D2-4 — 사이트 수정은 연락처를 DB에서 통째로 갈아 끼운다")
    void updatingASiteReplacesItsContactRows() {
        // Given
        ContractDetail created = contractCommandService.create(MANAGER_ID, contract("D연락처교체"));
        SiteView site = contractCommandService.addSite(MANAGER_ID, created.id(), new SiteCommand(
                "국가생명윤리정책원", SiteChannel.ENT, null, null,
                List.of(new ContactCommand(ContactParty.CLIENT, "김승윤", "차장", null, null),
                        new ContactCommand(ContactParty.CONTRACTOR, "박민수", "부장", null, null))));

        // When — 한 명만 보낸다. 보내지 않은 둘은 "그대로 둔다"가 아니라 "없다"로 읽힌다
        contractCommandService.updateSite(MANAGER_ID, site.id(), new SiteCommand(
                "국가생명윤리정책원", SiteChannel.ENT, null, ENGINEER_ID,
                List.of(new ContactCommand(ContactParty.CLIENT, "정유진", null, "043-717-7822",
                        null))), 0L);

        // Then
        assertThat(contactRepository.findBySiteIdInOrderByIdAsc(List.of(site.id())))
                .singleElement()
                .satisfies(contact -> assertThat(contact.getRaw()).isEqualTo("정유진 043-717-7822"));
    }

    @Test
    @DisplayName("D2-1·D2-4 — 쓰기는 감사 행을 남기고, 프로젝트 스코프가 아니다")
    void writesAreRecordedOutsideProjectScope() {
        // Given
        ContractDetail created = contractCommandService.create(MANAGER_ID, contract("D감사"));
        contractCommandService.addSite(MANAGER_ID, created.id(), new SiteCommand(
                "제주대학교병원", null, null, null, List.of()));

        // Then
        AuditRecord contractRow = auditOf("MaintenanceContract", created.id());
        assertThat(contractRow.action()).isEqualTo(AuditAction.CREATE);
        assertThat(contractRow.actorId()).isEqualTo(MANAGER_ID);
        assertThat(contractRow.after()).containsEntry("name", "D감사");
        // 계약은 프로젝트 스코프가 아니다 — 프로젝트별 이력(G2-2)에 섞이면 안 된다
        assertThat(contractRow.projectId()).isNull();
        assertThat(auditRows("MaintenanceSite")).isNotEmpty();
    }

    @Test
    @DisplayName("D2-2 — 한 번 수정하면 version은 한 칸만 오르고, 옛 version은 409다")
    void updateBumpsVersionOnceAndRejectsStaleRetries() {
        // Given
        ContractDetail created = contractCommandService.create(MANAGER_ID, contract("D낙관락"));
        assertThat(created.version()).isZero();

        // When
        ContractDetail updated = contractCommandService.update(MANAGER_ID, created.id(),
                contract("D낙관락 연장"), created.version());

        // Then — 두 칸 오르면 더러워진 세션에 질의한 것이다(conventions §4의 실측 사고)
        assertThat(updated.version()).isEqualTo(1L);
        assertThat(updated.name()).isEqualTo("D낙관락 연장");
        assertThatExceptionOfType(StaleVersionException.class)
                .isThrownBy(() -> contractCommandService.update(
                        MANAGER_ID, created.id(), contract("D낙관락 재시도"), created.version()));
    }

    @Test
    @DisplayName("D2-3 — 계약 관리 플래그가 없는 팀원은 403이고 아무것도 만들어지지 않는다")
    void memberWithoutTheFlagCannotWrite() {
        // Given
        long before = contractRepository.count();

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> contractCommandService.create(MEMBER_ID, contract("D거절")));
        assertThat(contractRepository.count()).isEqualTo(before);
    }

    private static ContractCommand contract(String name) {
        return new ContractCommand("㈜가온아이", name, ContractStatus.ACTIVE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                61320000L, 5110000L, MANAGER_ID, "검색엔진", "그룹웨어", "월 1회", null);
    }

    private AuditRecord auditOf(String entityType, long entityId) {
        return auditRows(entityType).stream()
                .filter(record -> record.entityId() == entityId)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        entityType + " " + entityId + " 감사 행이 없다"));
    }

    private List<AuditRecord> auditRows(String entityType) {
        return auditQueryService.findAll(PageRequest.of(0, 200)).stream()
                .filter(record -> entityType.equals(record.entityType()))
                .toList();
    }
}
