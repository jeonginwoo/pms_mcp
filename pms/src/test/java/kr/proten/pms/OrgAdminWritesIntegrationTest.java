package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.audit.AuditQueryService;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.OrgUnitService;
import kr.proten.pms.person.service.PersonService;
import kr.proten.pms.person.service.dto.OrgUnitMoveResult;
import kr.proten.pms.person.service.dto.PersonSummary;
import kr.proten.pms.person.service.dto.UpdatePersonCommand;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.OrgUnit;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.VisibilityScope;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.AssignmentView;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * EPIC E 조직·계정 쓰기 관통 (E2-2 인력 수정 · E1-1 소속 이동 · E3-2 조직 개명).
 *
 * <p>단위 테스트가 직급·권한 그룹의 규칙을 이미 고정하므로 여기서 보는 것은 <b>실물에서만
 * 드러나는 것</b>이다: 감사 행이 실제로 남는지, 소속 이동 경고가 <b>모듈 경계를 건너</b>
 * 배정 건수를 세어 오는지({@code AssignmentCountPort} — project가 구현), 이름이 바뀐 뒤
 * 조회가 새 이름을 내는지.
 *
 * <p>공유 컨테이너를 쓰므로 전용 id 블록(6xx)을 쓴다. <b>공유 픽스처 행을 바꾸지
 * 않는 것이 특히 중요하다</b>(2026-08-24 실측): 개명 테스트가 처음에
 * {@code PersonFixtures.SI_TEAM_ID}를 바꿨더니 그 행의 {@code @Version}이 올라가,
 * 같은 픽스처를 다시 저장하는 다른 통합 테스트 4개가 낙관적 락으로 무너졌다.
 * <b>쓰기 테스트는 자기가 만든 행만 건드린다.</b>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgAdminWritesIntegrationTest extends PostgresTestBase {
    private static final long ADMIN_GROUP_ID = 601L;
    private static final long MEMBER_GROUP_ID = 604L;

    private static final long ADMIN_ID = 601L;
    private static final long MOVER_ID = 602L;
    private static final long IDLE_ID = 603L;
    /**
     * 개명 확인 전용 인원 — 어떤 테스트도 이 사람을 옮기지 않는다.
     *
     * <p>이동 테스트가 옮기는 사람으로 개명을 확인하면 <b>실행 순서에 답이 달라진다</b>:
     * 이동이 먼저 돌면 그 사람은 이미 다른 조직에 있다. JUnit은 순서를 보장하지 않으므로
     * 검증 대상을 각 테스트가 따로 갖는다.
     */
    private static final long RENAME_WATCHER_ID = 604L;
    /** 퇴사 표시 전용 인원 — 다른 테스트가 이 사람을 비활성하지 않는다. */
    private static final long LEAVER_ID = 605L;

    private static final long SENIOR_GRADE_ID = 611L;
    private static final long JUNIOR_GRADE_ID = 612L;

    /** 개명 전용 노드 — 공유 픽스처를 바꾸면 다른 테스트의 저장이 낙관적 락에 걸린다. */
    private static final long RENAME_TARGET_ID = 621L;

    @Autowired
    private OrgUnitRepository orgUnitRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private PermissionGroupRepository permissionGroupRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private PersonService personService;
    @Autowired
    private OrgUnitService orgUnitService;
    @Autowired
    private ProjectCommandService projectCommandService;
    @Autowired
    private ProjectQueryService projectQueryService;
    @Autowired
    private AuditQueryService auditQueryService;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        gradeRepository.saveAll(List.of(
                Grade.of(SENIOR_GRADE_ID, "수석", 1.5), Grade.of(JUNIOR_GRADE_ID, "주임", 0.8)));
        permissionGroupRepository.saveAll(List.of(
                PersonFixtures.group(ADMIN_GROUP_ID, "E관리자", VisibilityScope.COMPANY,
                        OrgPermission.CREATE_PROJECT, OrgPermission.MANAGE_ORG),
                PersonFixtures.group(MEMBER_GROUP_ID, "E팀원", VisibilityScope.SELF)));
        orgUnitRepository.save(
                OrgUnit.of(RENAME_TARGET_ID, PersonFixtures.COMPANY_ID, "E개명대상팀"));
        personRepository.saveAll(List.of(
                PersonFixtures.person(ADMIN_ID, "E대표", PersonFixtures.COMPANY_ID, ADMIN_GROUP_ID),
                PersonFixtures.person(MOVER_ID, "E이동대상", RENAME_TARGET_ID, MEMBER_GROUP_ID),
                PersonFixtures.person(IDLE_ID, "E무배정", RENAME_TARGET_ID, MEMBER_GROUP_ID),
                PersonFixtures.person(RENAME_WATCHER_ID, "E잔류", RENAME_TARGET_ID,
                        MEMBER_GROUP_ID),
                PersonFixtures.person(LEAVER_ID, "E퇴사자", RENAME_TARGET_ID, MEMBER_GROUP_ID)));

        // 이동 경고를 만들려면 진행 중 배정이 있어야 한다 — 그 건수를 project가 세어 준다
        projectCommandService.create(ADMIN_ID, new CreateProjectCommand(
                "(주)가온아이",
                "E 이동 경고용 구축",
                "검색엔진",
                Engagement.REMOTE,
                2.0,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31),
                List.of(new AssignmentSpec(MOVER_ID, ProjectRole.PM, null, null, 0.5))));
    }

    @Test
    @DisplayName("E1-2 — 진행 중 배정이 있으면 이동을 허용하되 경고를 응답에 싣는다")
    void moveWithLiveAssignmentsWarnsButSucceeds() {
        OrgUnitMoveResult moved = personService.moveOrgUnit(
                ADMIN_ID, MOVER_ID, PersonFixtures.OTHER_DIVISION_ID);

        // 막지 않는다 — 막으면 실제 조직 개편을 시스템이 거부하게 된다
        assertThat(moved.person().id()).isEqualTo(MOVER_ID);
        assertThat(moved.activeAssignments()).isEqualTo(1);
        assertThat(moved.warning()).contains("진행 중인 배정 1건");
        // 소속이 실제로 바뀌었다 — 가시성은 이 필드에서 파생되므로 다음 요청부터 새 범위다
        assertThat(personRepository.findById(MOVER_ID).orElseThrow().getOrgUnitId())
                .isEqualTo(PersonFixtures.OTHER_DIVISION_ID);
    }

    @Test
    @DisplayName("E1-2 — 배정이 없으면 경고가 없다 (null)")
    void moveWithoutAssignmentsHasNoWarning() {
        OrgUnitMoveResult moved = personService.moveOrgUnit(
                ADMIN_ID, IDLE_ID, PersonFixtures.OTHER_DIVISION_ID);

        assertThat(moved.activeAssignments()).isZero();
        assertThat(moved.warning()).isNull();
    }

    @Test
    @DisplayName("E2-2 — 이름·직급·그룹을 바꾸면 바뀐 필드만 감사에 남는다")
    void updatePersonRecordsOnlyChangedFields() {
        PersonSummary updated = personService.update(ADMIN_ID, new UpdatePersonCommand(
                IDLE_ID, "E개명됨", PersonFixtures.OTHER_DIVISION_ID, SENIOR_GRADE_ID,
                MEMBER_GROUP_ID, 0));

        assertThat(updated.name()).isEqualTo("E개명됨");
        assertThat(updated.grade()).isEqualTo("수석");

        AuditRecord latest = personAudit(IDLE_ID);
        // 그룹은 그대로 넘겼으므로 diff에 없어야 한다 — "바뀐 필드만"이 규칙이다
        assertThat(latest.after()).containsKeys("name", "gradeId").doesNotContainKey("groupId");
        assertThat(latest.before()).containsEntry("name", "E무배정");
    }

    @Test
    @DisplayName("E2-5 — 시스템 계정은 수정도 이동도 422 IMMUTABLE_ACCOUNT")
    void systemAccountCannotBeEdited() {
        long systemId = 699L;
        personRepository.save(PersonFixtures.systemAccount(
                systemId, PersonFixtures.COMPANY_ID, ADMIN_GROUP_ID));

        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> personService.update(ADMIN_ID, new UpdatePersonCommand(
                        systemId, "바꾸기", PersonFixtures.COMPANY_ID, SENIOR_GRADE_ID,
                        ADMIN_GROUP_ID, 0)));
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> personService.moveOrgUnit(
                        ADMIN_ID, systemId, PersonFixtures.OTHER_DIVISION_ID));
    }

    @Test
    @DisplayName("E3-5 — 노드를 옮기면 소속 인원의 부문 표시가 새 경로를 따른다")
    void moveOrgUnitFlowsThroughToDivision() {
        // 개편 대상 전용 노드 2개 — 공유 픽스처를 건드리면 다른 통합 테스트가 무너진다
        long newDivisionId = 622L;
        long movedTeamId = 623L;
        long memberId = 606L;
        orgUnitRepository.save(OrgUnit.of(newDivisionId, PersonFixtures.COMPANY_ID, "E신설부문"));
        orgUnitRepository.save(OrgUnit.of(movedTeamId, PersonFixtures.COMPANY_ID, "E이사대상팀"));
        personRepository.save(
                PersonFixtures.person(memberId, "E개편대상", movedTeamId, MEMBER_GROUP_ID));

        // 옮기기 전: 회사 직속이라 그 팀 자신이 부문이다(OrgTree.topDivisionIdOf 규약)
        assertThat(personService.getPerson(ADMIN_ID, memberId).division()).isEqualTo("E이사대상팀");

        orgUnitService.move(ADMIN_ID, movedTeamId, newDivisionId);

        // 옮긴 뒤: 인원·프로젝트는 orgUnitId만 들고 있으므로 부문 파생값이 저절로 따라온다.
        // 비정규화된 경로 컬럼이 있었다면 여기서 옛 부문이 나온다
        PersonSummary member = personService.getPerson(ADMIN_ID, memberId);
        assertThat(member.orgUnit()).isEqualTo("E이사대상팀");
        assertThat(member.division()).isEqualTo("E신설부문");
        // 감사 스냅샷은 JSON을 왕복하므로 숫자가 Integer로 돌아온다 — Long으로 비교하면
        // 값이 같아도 어긋난다(실측 2026-08-24)
        assertThat(personAuditLatest("OrgUnit", movedTeamId).after())
                .containsEntry("parentId", (int) newDivisionId);
    }

    @Test
    @DisplayName("E2-3 — 퇴사자의 배정은 이름을 잃지 않는다 (화면이 #id를 그리지 않게)")
    void deactivatedPersonKeepsDisplayNameOnAssignments() {
        ProjectDetail created = projectCommandService.create(ADMIN_ID, new CreateProjectCommand(
                "(주)가온아이",
                "E 퇴사자 배정 보존용 구축",
                "검색엔진",
                Engagement.REMOTE,
                1.0,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31),
                List.of(
                        new AssignmentSpec(ADMIN_ID, ProjectRole.PM, null, null, 0.0),
                        new AssignmentSpec(LEAVER_ID, ProjectRole.PARTICIPANT, null, null, 0.5))));

        personService.deactivate(ADMIN_ID, LEAVER_ID);

        AssignmentView leaver = projectQueryService.getProject(ADMIN_ID, created.id())
                .assignments().stream()
                .filter(assignment -> assignment.personId() == LEAVER_ID)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("퇴사자의 배정 행이 사라졌다"));

        // 배정은 남는다(B2-1) — 그러면 이름도 남아야 한다. 활성 필터를 든 조회는
        // 이 자리를 null로 만들고 화면은 `#605`를 그렸다(2026-08-24 수정 전 거동)
        assertThat(leaver.personName()).isEqualTo("E퇴사자");
        assertThat(leaver.personActive()).isFalse();
        // 재직자는 같은 응답에서 true다 — 플래그가 사람별로 갈린다
        assertThat(projectQueryService.getProject(ADMIN_ID, created.id()).assignments().stream()
                .filter(assignment -> assignment.personId() == ADMIN_ID)
                .findFirst()
                .orElseThrow()
                .personActive()).isTrue();
    }

    @Test
    @DisplayName("E3-2 — 개명하면 소속 인원 표시가 저절로 따라온다 (비정규화 컬럼이 없다)")
    void renameFlowsThroughToPeople() {
        orgUnitService.rename(ADMIN_ID, RENAME_TARGET_ID, "E개명팀");

        // 인원은 orgUnitId만 들고 있으므로 참조가 그대로 새 이름을 낸다 —
        // 비정규화된 이름 컬럼이 있었다면 여기서 옛 이름이 나온다
        assertThat(personService.getPerson(ADMIN_ID, RENAME_WATCHER_ID).orgUnit())
                .isEqualTo("E개명팀");
        assertThat(personAuditLatest("OrgUnit", RENAME_TARGET_ID).after())
                .containsEntry("name", "E개명팀");
    }

    private AuditRecord personAudit(long personId) {
        return personAuditLatest("Person", personId);
    }

    private AuditRecord personAuditLatest(String entityType, long entityId) {
        return auditQueryService.findAll(PageRequest.of(0, 50)).getContent().stream()
                .filter(record -> record.entityType().equals(entityType))
                .filter(record -> record.entityId() != null && record.entityId() == entityId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "감사 행이 없다: %s #%d".formatted(entityType, entityId)));
    }
}
