package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.audit.AuditAction;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.audit.AuditSource;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.AuditViewService;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.VisibilityScope;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.service.ProjectCommandService;
import kr.proten.pms.project.service.ProjectQueryService;
import kr.proten.pms.project.service.dto.AssignmentSpec;
import kr.proten.pms.project.service.dto.CreateProjectCommand;
import kr.proten.pms.project.service.dto.EditProjectCommand;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * 감사 조회 두 뷰 관통 검증 (G1-3 · G2-2) — 실물 DB에 이력을 쌓고 두 뷰로 읽는다.
 *
 * <p>단위 테스트가 정렬·변환을 보는 것과 달리, 여기서는 <b>기록과 조회가 같은 행을
 * 두고 만나는지</b>를 본다: 저장은 `AuditTrailImpl`이 JSON으로 굳히고 조회는
 * `AuditRecordFactory`가 다시 펴므로, 두 지점이 어긋나면 저장은 성공하고 조회만
 * 비는 형태로 이력이 깨진다 — 그 어긋남은 관통해서만 드러난다.
 *
 * <p>권한·가시성 판정은 두 뷰의 <b>호출자 쪽</b>에 있다(person·project). 이 테스트가
 * 403·404를 함께 보는 이유는 판정이 조회 앞에 서 있다는 배치를 고정하기 위해서다 —
 * "없는 것은 로직이지 권한이 아니다"의 반대편, 즉 구현이 들어온 뒤에도 판정이
 * 먼저라는 확인이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditReadViewsIntegrationTest extends PostgresTestBase {
    private static final long ADMIN_GROUP_ID = 1L;
    private static final long TEAM_LEAD_GROUP_ID = 3L;
    private static final long MEMBER_GROUP_ID = 4L;

    private static final long ADMIN_ID = 1L;
    private static final long SI_LEAD_ID = 202L;
    private static final long SI_MEMBER_ID = 203L;
    private static final long OTHER_DIVISION_MEMBER_ID = 206L;

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
    private AuditViewService auditViewService;

    private long projectId;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        gradeRepository.saveAll(List.of(Grade.of(11L, "수석", 1.5), Grade.of(12L, "주임", 1.0)));
        permissionGroupRepository.saveAll(List.of(
                PersonFixtures.group(ADMIN_GROUP_ID, "관리자", VisibilityScope.COMPANY,
                        OrgPermission.CREATE_PROJECT,
                        OrgPermission.MANAGE_CONTRACTS,
                        OrgPermission.MANAGE_ALL_PROJECTS,
                        OrgPermission.MANAGE_ORG),
                PersonFixtures.group(TEAM_LEAD_GROUP_ID, "팀장", VisibilityScope.TEAM,
                        OrgPermission.CREATE_PROJECT),
                PersonFixtures.group(MEMBER_GROUP_ID, "팀원", VisibilityScope.SELF)));
        personRepository.saveAll(List.of(
                PersonFixtures.person(ADMIN_ID, "대표", PersonFixtures.COMPANY_ID, ADMIN_GROUP_ID),
                PersonFixtures.person(SI_LEAD_ID, "감사팀장", PersonFixtures.SI_TEAM_ID,
                        TEAM_LEAD_GROUP_ID),
                PersonFixtures.person(SI_MEMBER_ID, "감사팀원", PersonFixtures.SI_TEAM_ID,
                        MEMBER_GROUP_ID),
                PersonFixtures.person(OTHER_DIVISION_MEMBER_ID, "감사타부문원",
                        PersonFixtures.OTHER_DIVISION_ID, MEMBER_GROUP_ID)));

        projectId = projectCommandService.create(SI_LEAD_ID, new CreateProjectCommand(
                "(주)가온아이",
                "감사 이력 확인용 구축",
                "검색엔진",
                Engagement.REMOTE,
                2.0,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31),
                List.of(new AssignmentSpec(SI_MEMBER_ID, ProjectRole.PM, null, null, 0.5)))).id();

        // 상태 전이 + 진척률로 이력을 두 건 더 쌓는다 — 최신순 검증에 순서가 필요하다
        // 정보 수정은 PM·PL만(A5-3) — 생성자가 아니라 PM이 호출한다
        projectCommandService.edit(SI_MEMBER_ID, new EditProjectCommand(
                projectId,
                "(주)가온아이",
                "감사 이력 확인용 구축",
                "검색엔진",
                Engagement.REMOTE,
                2.0,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31),
                ProjectStatus.ORDER_CONFIRMED,
                0L));
    }

    @Test
    @DisplayName("G2-2 — 그 프로젝트의 이력만 최신순으로, before/after 스냅샷까지 채워 온다")
    void projectView_returnsOwnHistoryNewestFirst() {
        List<AuditRecord> history = projectQueryService
                .listAudit(SI_LEAD_ID, projectId, PageRequest.of(0, 20))
                .getContent();

        assertThat(history).isNotEmpty();
        assertThat(history).allSatisfy(record ->
                assertThat(record.projectId()).isEqualTo(projectId));
        assertThat(history)
                .extracting(AuditRecord::createdAt)
                .isSortedAccordingTo((left, right) -> right.compareTo(left));

        // 기록(AuditTrailImpl)과 조회(AuditRecordFactory)가 같은 맵 표현으로 만난다
        AuditRecord created = history.getLast();
        assertThat(created.action()).isEqualTo(AuditAction.CREATE);
        assertThat(created.before()).isNull();
        assertThat(created.after()).containsEntry("name", "감사 이력 확인용 구축");
        assertThat(created.actorId()).isEqualTo(SI_LEAD_ID);
        assertThat(created.source()).isEqualTo(AuditSource.WEB);
    }

    @Test
    @DisplayName("G2-3 — 가시성 밖 호출자에게는 403이 아니라 404다 (상세 조회와 같은 관문)")
    void projectView_outsideVisibilityIsHidden() {
        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() ->
                projectQueryService.listAudit(
                        OTHER_DIVISION_MEMBER_ID, projectId, PageRequest.of(0, 20)));
    }

    @Test
    @DisplayName("G1-3 — 관리 플래그 보유자는 통합 로그를 최신순으로 받는다")
    void unifiedView_servesManagers() {
        List<AuditRecord> all = auditViewService.listAll(ADMIN_ID, PageRequest.of(0, 50))
                .getContent();

        assertThat(all).isNotEmpty();
        assertThat(all)
                .extracting(AuditRecord::createdAt)
                .isSortedAccordingTo((left, right) -> right.compareTo(left));
        // 통합 로그는 프로젝트 스코프 밖 행까지 담는 유일한 뷰다 — projectId가 null인
        // 행(조직·계정 변경)이 섞여도 걸러지지 않는다
        assertThat(all).anySatisfy(record ->
                assertThat(record.projectId()).isEqualTo(projectId));
    }

    @Test
    @DisplayName("G1-3 — 관리 플래그 없는 호출자는 403이고 조회로 넘어가지 않는다")
    void unifiedView_requiresManageFlag() {
        assertThatExceptionOfType(ForbiddenException.class).isThrownBy(() ->
                auditViewService.listAll(SI_MEMBER_ID, PageRequest.of(0, 20)));
    }

    @Test
    @DisplayName("정렬은 호출자가 뒤집을 수 없다 — 이력은 시간 순서가 의미의 일부다")
    void sortIsNotNegotiable() {
        List<AuditRecord> ascendingAttempt = projectQueryService
                .listAudit(SI_LEAD_ID, projectId,
                        PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("createdAt")))
                .getContent();

        assertThat(ascendingAttempt)
                .extracting(AuditRecord::createdAt)
                .isSortedAccordingTo((left, right) -> right.compareTo(left));
    }
}
