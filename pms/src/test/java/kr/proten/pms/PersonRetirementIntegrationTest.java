package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.PersonService;
import kr.proten.pms.person.service.dto.OrgUnitMoveResult;
import kr.proten.pms.person.service.dto.PersonDeactivateResult;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.OrgUnit;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.VisibilityScope;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.ProjectLifecycleService;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.UpdateProgressCommand;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 퇴사(비활성) 처리 관통 — AC E2-3 + PRD-pms §12 ③ · 실물 PostgreSQL.
 *
 * <p><b>이 클래스가 실물이어야 하는 이유는 완료 프로젝트다.</b> 단위 테스트는 포트가
 * 돌려주는 목록을 그대로 믿으므로 "완료 건이 빠지는가"를 증명할 수 없다 — 그 판정은
 * 질의 안에 있고, 질의는 실제 조인이라야 돈다. 그리고 그 자리가 바로 <b>2026-08-26에
 * 결함이 나온 자리</b>다: 완료 프로젝트의 배정이 {@code ACTIVE}로 남아 있어(시드 실측
 * 462건 중 384건) 이동 경고가 한 사람에게 "진행 중인 배정 128건"이라 답했고 실제로
 * 물려 있는 것은 5건이었다. 그래서 여기서 <b>완료 프로젝트를 실제로 만들어</b> 세지도
 * 끊지도 않는지 본다.
 *
 * <p>나머지 둘은 트랜잭션이 진짜 하나인지다: PM 거절이 <b>DB에 아무것도 남기지 않는지</b>
 * (배정도 인원도), 참여자 종료가 <b>행의 상태로</b> 확인되는지. 목은 "저장을 부르지
 * 않았다"까지만 말한다.
 *
 * <p>전용 id 블록(11xx)을 쓴다 — 공유 픽스처 행을 <b>바꾸지</b> 않는 것이 규칙이다
 * (2026-08-24 실측: 남의 행을 건드리면 그 행의 version이 올라 다른 통합 테스트가
 * 낙관적 락으로 무너진다). 이 파일의 리터럴 id는 1101~1106·1121·1131이다.
 *
 * <p><b>비활성 대상은 테스트마다 다른 사람이다</b>: 비활성은 되돌릴 수 없고 JUnit은
 * 실행 순서를 보장하지 않으므로, 한 사람을 두 테스트가 비활성하면 먼저 도는 쪽이
 * 나머지의 전제를 부순다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonRetirementIntegrationTest extends PostgresTestBase {
    private static final long ADMIN_GROUP_ID = 1101L;
    private static final long MEMBER_GROUP_ID = 1102L;

    private static final long ADMIN_ID = 1101L;
    /** 참여자로만 물린 퇴사자 — 자동 종료 대상. */
    private static final long PARTICIPANT_ID = 1102L;
    /** PM으로 물린 퇴사자 — 교체를 요구받는다. 다른 테스트가 이 사람을 비활성하지 않는다. */
    private static final long MANAGER_ID = 1103L;
    /** 완료 프로젝트에만 물린 퇴사자 — 세지도 끊지도 않아야 한다. */
    private static final long RETIRED_FROM_DONE_ID = 1104L;
    /** 이름 보존 확인 전용 퇴사자 — 비활성 대상이 테스트마다 따로다(아래 각주). */
    private static final long NAME_KEEPER_ID = 1105L;
    /**
     * 이동 경고 확인 전용 — <b>어떤 테스트도 이 사람을 비활성하지 않는다</b>.
     *
     * <p>비활성은 되돌릴 수 없고 JUnit은 실행 순서를 보장하지 않는다. 위 셋을 한 사람으로
     * 묶었다가는 먼저 도는 테스트가 나머지의 전제를 부순다 — {@code moveOrgUnit}은
     * 활성 인원만 편집 대상으로 보므로 404가 된다.
     */
    private static final long MOVE_WATCHER_ID = 1106L;

    private static final long TEAM_ID = 1121L;
    private static final long GRADE_ID = 1131L;

    @Autowired
    private OrgUnitRepository orgUnitRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private PermissionGroupRepository permissionGroupRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private ProjectAssignmentRepository assignmentRepository;
    @Autowired
    private PersonService personService;
    @Autowired
    private ProjectCommandService projectCommandService;
    @Autowired
    private ProjectLifecycleService projectLifecycleService;
    @Autowired
    private ProjectQueryService projectQueryService;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        orgUnitRepository.save(OrgUnit.of(TEAM_ID, PersonFixtures.COMPANY_ID, "E퇴사팀"));
        gradeRepository.save(Grade.of(GRADE_ID, "E퇴사선임", 1.0));
        permissionGroupRepository.saveAll(List.of(
                PersonFixtures.group(ADMIN_GROUP_ID, "E퇴사관리자", VisibilityScope.COMPANY,
                        OrgPermission.CREATE_PROJECT, OrgPermission.MANAGE_ORG),
                PersonFixtures.group(MEMBER_GROUP_ID, "E퇴사팀원", VisibilityScope.COMPANY)));
        personRepository.saveAll(List.of(
                person(ADMIN_ID, "E퇴사관리자", ADMIN_GROUP_ID),
                person(PARTICIPANT_ID, "E참여자퇴사", MEMBER_GROUP_ID),
                person(MANAGER_ID, "E피엠퇴사", MEMBER_GROUP_ID),
                person(RETIRED_FROM_DONE_ID, "E완료만", MEMBER_GROUP_ID),
                person(NAME_KEEPER_ID, "E이름보존", MEMBER_GROUP_ID),
                person(MOVE_WATCHER_ID, "E이동관찰", MEMBER_GROUP_ID)));
    }

    @Test
    @DisplayName("§12 ③ — 참여자 배정은 함께 종료되고 무엇이 끊겼는지 응답에 실린다")
    void deactivateClosesParticipantAssignments() {
        // Given: 관리자가 PM인 프로젝트에 퇴사자가 참여자로 물려 있다
        ProjectDetail project = createProject("E퇴사 참여 프로젝트", PARTICIPANT_ID);

        // When
        PersonDeactivateResult result = personService.deactivate(ADMIN_ID, PARTICIPANT_ID);

        // Then: 살려 두면 퇴사자가 가동률 모집단에 남는다(C1-4)
        assertThat(result.closedAssignments()).isEqualTo(1);
        assertThat(result.projects()).containsExactly("E퇴사 참여 프로젝트");
        assertThat(result.notice()).contains("배정 1건");
        assertThat(personRepository.findById(PARTICIPANT_ID).orElseThrow().isActive()).isFalse();
        // 목이 아니라 행의 상태로 본다 — 행은 남는다(지난달 가동률은 그때의 배정으로 센다)
        assertThat(liveCountOf(PARTICIPANT_ID)).isZero();
        assertThat(assignmentRepository.findByProjectIdAndStatus(
                        project.id(), AssignmentStatus.CLOSED))
                .extracting(assignment -> assignment.getPersonId())
                .contains(PARTICIPANT_ID);
    }

    @Test
    @DisplayName("§12 ③ — PM으로 물려 있으면 409 IN_USE, 배정도 인원도 그대로다")
    void deactivateWhileManagerIsRejectedAndChangesNothing() {
        // Given: 퇴사자가 PM인 프로젝트 — 그대로 비우면 PM 공석이 된다(A6-5)
        ProjectDetail managed = createProjectManagedBy("E퇴사 PM 프로젝트", MANAGER_ID);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> personService.deactivate(ADMIN_ID, MANAGER_ID))
                .satisfies(thrown -> {
                    assertThat(thrown.code()).isEqualTo(ErrorCode.IN_USE);
                    assertThat(thrown.getMessage()).contains("E퇴사 PM 프로젝트");
                });

        // 아무것도 안 바뀐다 — 목은 "저장을 안 불렀다"까지만, 실물은 DB가 그대로임을 말한다
        assertThat(personRepository.findById(MANAGER_ID).orElseThrow().isActive()).isTrue();
        assertThat(liveCountOf(MANAGER_ID)).isEqualTo(1);
        assertThat(assignmentRepository.findByProjectIdAndStatus(
                managed.id(), AssignmentStatus.ACTIVE)).isNotEmpty();
    }

    @Test
    @DisplayName("선행 결함 회귀 — 완료 프로젝트의 배정은 세지도 끊지도 않는다")
    void completedProjectAssignmentsAreNeitherCountedNorClosed() {
        // Given: 완료 프로젝트에만 물린 사람. 배정 행은 ACTIVE로 남아 있다 —
        // 완료가 배정을 종료하지 않기 때문이고(B2-1은 명시적 수동 종료다) 그것이
        // 2026-08-26에 드러난 결함의 원인이다
        ProjectDetail done = givenCompleted("E퇴사 완료 프로젝트", RETIRED_FROM_DONE_ID);
        assertThat(assignmentRepository.findByProjectIdAndStatus(
                        done.id(), AssignmentStatus.ACTIVE))
                .extracting(assignment -> assignment.getPersonId())
                .contains(RETIRED_FROM_DONE_ID);

        // When
        PersonDeactivateResult result = personService.deactivate(ADMIN_ID, RETIRED_FROM_DONE_ID);

        // Then: 물려 있지 않으므로 안내도 종료도 없다
        assertThat(result.closedAssignments()).isZero();
        assertThat(result.notice()).isNull();
        // 그 행은 그대로 ACTIVE다 — 퇴사가 과거 배정의 상태를 다시 쓰지 않는다
        assertThat(assignmentRepository.findByProjectIdAndStatus(
                        done.id(), AssignmentStatus.ACTIVE))
                .extracting(assignment -> assignment.getPersonId())
                .contains(RETIRED_FROM_DONE_ID);
    }

    @Test
    @DisplayName("E2-3 — 퇴사자의 (완료 프로젝트) 배정은 이름을 잃지 않는다")
    void completedProjectAssignmentKeepsTheLeaverDisplayName() {
        // 2026-08-24가 잠갔던 성질이 옮겨 온 자리다: 활성 필터를 든 조회는 이름을
        // null로 만들고 화면이 `#1104`를 그렸다. §12 ③ 이후로 <b>비활성 인원이 ACTIVE
        // 배정을 들고 있는 경우</b>가 여기 하나로 좁혀졌다 — 퇴사는 완료 건을 끊지 않는다
        ProjectDetail done = givenCompleted("E퇴사 이름보존 프로젝트", NAME_KEEPER_ID);
        personService.deactivate(ADMIN_ID, NAME_KEEPER_ID);

        assertThat(projectQueryService.getProject(ADMIN_ID, done.id()).assignments())
                .filteredOn(assignment -> assignment.personId() == NAME_KEEPER_ID)
                .singleElement()
                .satisfies(assignment -> {
                    assertThat(assignment.personName()).isEqualTo("E이름보존");
                    assertThat(assignment.personActive()).isFalse();
                });
    }

    @Test
    @DisplayName("E1-2 회귀 — 이동 경고도 완료 프로젝트의 배정은 세지 않는다")
    void moveWarningIgnoresCompletedProjects() {
        // 같은 판정을 두 유스케이스가 공유한다(ProjectStatus.LIVE) — 한쪽만 고쳐지면
        // 두 화면이 다른 수를 낸다. 그 공유를 여기서 잠근다
        givenCompleted("E퇴사 이동경고 프로젝트", MOVE_WATCHER_ID);

        OrgUnitMoveResult moved = personService.moveOrgUnit(
                ADMIN_ID, MOVE_WATCHER_ID, PersonFixtures.OTHER_DIVISION_ID);

        assertThat(moved.activeAssignments()).isZero();
        assertThat(moved.warning()).isNull();
    }

    private long liveCountOf(long personId) {
        return assignmentRepository.countLiveByPerson(
                personId, AssignmentStatus.ACTIVE, ProjectStatus.live());
    }

    private static Person person(long id, String name, long groupId) {
        return Person.of(id, name, TEAM_ID, GRADE_ID, groupId, 1.0, true, false, true);
    }

    /** 관리자가 PM이고 대상자가 참여자인 프로젝트 (계약대기 — 여전히 "물려 있는" 상태다). */
    private ProjectDetail createProject(String name, long participantId) {
        return projectCommandService.create(ADMIN_ID, new CreateProjectCommand(
                "(주)가온아이", name, "검색엔진", Engagement.REMOTE, 2.0,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31),
                List.of(
                        new AssignmentSpec(ADMIN_ID, ProjectRole.PM, null, null, 0.5),
                        new AssignmentSpec(participantId, ProjectRole.PARTICIPANT,
                                null, null, 0.5))));
    }

    private ProjectDetail createProjectManagedBy(String name, long managerId) {
        return projectCommandService.create(ADMIN_ID, new CreateProjectCommand(
                "(주)가온아이", name, "검색엔진", Engagement.REMOTE, 2.0,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31),
                List.of(new AssignmentSpec(managerId, ProjectRole.PM, null, null, 0.5))));
    }

    /** §5 전이를 실제로 밟아 완료까지 보낸다 — 상태만 바꿔 넣으면 규칙을 우회한 데이터가 된다. */
    private ProjectDetail givenCompleted(String name, long participantId) {
        ProjectDetail created = createProject(name, participantId);
        ProjectDetail confirmed = projectCommandService.edit(ADMIN_ID,
                editCommand(created.id(), name, ProjectStatus.ORDER_CONFIRMED, created.version()));
        ProjectDetail inProgress = projectCommandService.edit(ADMIN_ID,
                editCommand(created.id(), name, ProjectStatus.IN_PROGRESS, confirmed.version()));
        var progressed = projectLifecycleService.updateProgress(ADMIN_ID,
                new UpdateProgressCommand(created.id(), 100, inProgress.version(), true));

        return projectLifecycleService.complete(ADMIN_ID, created.id(), progressed.version());
    }

    private EditProjectCommand editCommand(
            long projectId, String name, ProjectStatus status, long version) {
        return new EditProjectCommand(projectId, "(주)가온아이", name, "검색엔진",
                Engagement.REMOTE, 2.0, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31), status, version);
    }
}
