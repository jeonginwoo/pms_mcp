package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.StaleVersionException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.service.ContractCommandService;
import kr.proten.pms.maintenance.service.IssueCommandService;
import kr.proten.pms.maintenance.service.IssueQueryService;
import kr.proten.pms.maintenance.service.MaintenanceQueryService;
import kr.proten.pms.maintenance.service.dto.CommentView;
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
 * 이슈 정정·삭제·코멘트 수정 관통 — AC D3-5·D3-6·D3-7 (2026-08-26 신설) · 실물 PostgreSQL.
 *
 * <p><b>착수 계기가 이 클래스의 첫 테스트다</b>: 등록 경로는 있는데 <b>제목 오타를 고칠
 * 방법이 아예 없었다</b>({@code PATCH}가 status·assigneeId 두 칸만 받았다).
 *
 * <p>여기서 보는 것은 <b>관문이 행위별로 갈리는지</b>다 — 그 판정은 세 사람과 실제 행이
 * 있어야 성립한다. 등록·처리·코멘트 추가는 US-D3대로 전원이고(게시판은 전사 공개다),
 * <b>정정·삭제만</b> 등록자·담당자·"계약 관리" 플래그로 좁혀진다. 목으로는 "관문을
 * 불렀다"까지만 보이고 <b>누가 통과하고 누가 막히는지</b>는 보이지 않는다.
 *
 * <p>soft 삭제도 실물이라야 증명된다: 행이 남은 채 <b>목록·상세·계약 요약 전부에서</b>
 * 빠지는지가 질의의 몫이기 때문이다.
 *
 * <p>전용 id 블록(12xx)을 쓴다 — 공유 픽스처 행을 바꾸지 않는 것이 규칙이다
 * (2026-08-24 실측). 이 파일의 리터럴 id는 1201~1204·1221·1231이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaintenanceIssueCorrectionIntegrationTest extends PostgresTestBase {
    private static final long MANAGER_GROUP_ID = 1201L;
    private static final long MEMBER_GROUP_ID = 1202L;

    /** "계약 관리" 플래그 보유자 — 남의 이슈도 정정할 수 있다(시드 이슈의 유일한 손). */
    private static final long MANAGER_ID = 1201L;
    /** 등록자 — 자기 오타를 고친다. 플래그는 없다. */
    private static final long REPORTER_ID = 1202L;
    /** 사이트 담당 엔지니어 = 기본 담당자. 플래그는 없다. */
    private static final long ENGINEER_ID = 1203L;
    /** 아무 관계 없는 제3자 — 등록자도 담당자도 아니고 플래그도 없다. */
    private static final long OUTSIDER_ID = 1204L;

    private static final long TEAM_ID = 1221L;
    private static final long GRADE_ID = 1231L;

    @Autowired
    private OrgUnitRepository orgUnitRepository;
    @Autowired
    private GradeRepository gradeRepository;
    @Autowired
    private PermissionGroupRepository permissionGroupRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private ContractCommandService contractCommandService;
    @Autowired
    private IssueCommandService issueCommandService;
    @Autowired
    private IssueQueryService issueQueryService;
    @Autowired
    private MaintenanceQueryService maintenanceQueryService;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        orgUnitRepository.save(OrgUnit.of(TEAM_ID, PersonFixtures.COMPANY_ID, "D3정정팀"));
        gradeRepository.save(Grade.of(GRADE_ID, "D3정정선임", 1.0));
        permissionGroupRepository.saveAll(List.of(
                PersonFixtures.group(MANAGER_GROUP_ID, "D3정정계약관리", VisibilityScope.COMPANY,
                        OrgPermission.MANAGE_CONTRACTS),
                PersonFixtures.group(MEMBER_GROUP_ID, "D3정정팀원", VisibilityScope.COMPANY)));
        personRepository.saveAll(List.of(
                person(MANAGER_ID, "D3정정관리자", MANAGER_GROUP_ID),
                person(REPORTER_ID, "D3등록자", MEMBER_GROUP_ID),
                person(ENGINEER_ID, "D3담당자", MEMBER_GROUP_ID),
                person(OUTSIDER_ID, "D3제3자", MEMBER_GROUP_ID)));
    }

    // --- D3-5 정정 -----------------------------------------------------------

    @Test
    @DisplayName("D3-5 — 등록자가 자기 이슈의 제목 오타를 고친다 (착수 계기)")
    void reporterFixesOwnTitle() {
        IssueView created = register("로그인 지연 현상 문이", null);

        IssueView fixed = issueCommandService.process(REPORTER_ID, created.id(),
                new IssueEditCommand(null, null, null, "로그인 지연 현상 문의", null),
                created.version());

        assertThat(fixed.title()).isEqualTo("로그인 지연 현상 문의");
        // 등록자가 화자로 남아 있어야 이 권한이 성립한다
        assertThat(fixed.reporterId()).isEqualTo(REPORTER_ID);
        // 상태·담당은 건드리지 않았다 (PATCH 의미론)
        assertThat(fixed.statusCode()).isEqualTo(IssueStatus.RECEIVED);
    }

    @Test
    @DisplayName("D3-5 — 담당자도 고칠 수 있다 (처리하면서 내용을 다듬는다)")
    void assigneeCanCorrect() {
        IssueView created = register("본문 다듬기 대상", ENGINEER_ID);

        // 사이트의 담당 엔지니어가 기본 담당자다(D3-1) — 그 사실이 이 권한의 근거다
        assertThat(created.assignee()).isNotNull();
        assertThat(created.assignee().id()).isEqualTo(ENGINEER_ID);

        IssueView fixed = issueCommandService.process(ENGINEER_ID, created.id(),
                new IssueEditCommand(null, null, null, null, "재현 절차: 로그인 → 5초 대기"),
                created.version());

        assertThat(fixed.content()).isEqualTo("재현 절차: 로그인 → 5초 대기");
    }

    @Test
    @DisplayName("D3-5 — \"계약 관리\" 플래그 보유자는 남의 이슈도 고친다 (시드 이슈의 유일한 손)")
    void contractManagerCanCorrectOthersIssue() {
        IssueView created = register("관리자가 고칠 제목", null);

        IssueView fixed = issueCommandService.process(MANAGER_ID, created.id(),
                new IssueEditCommand(null, null, IssueType.REQUEST, "관리자가 고친 제목", null),
                created.version());

        assertThat(fixed.title()).isEqualTo("관리자가 고친 제목");
        assertThat(fixed.type()).isEqualTo(IssueType.REQUEST.label());
    }

    @Test
    @DisplayName("D3-5 — 등록자도 담당자도 아니고 플래그도 없으면 403, 아무것도 안 바뀐다")
    void outsiderCannotCorrect() {
        IssueView created = register("제3자가 못 고치는 제목", null);

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> issueCommandService.process(OUTSIDER_ID, created.id(),
                        new IssueEditCommand(null, null, null, "몰래 고친 제목", null),
                        created.version()));

        // 실물로 확인한다 — 목은 "관문에서 던졌다"까지만 말한다
        assertThat(issueQueryService.getIssue(created.id()).title())
                .isEqualTo("제3자가 못 고치는 제목");
    }

    @Test
    @DisplayName("D3-5 — 관문은 정정에만 걸린다: 제3자도 상태 전이·재배정은 할 수 있다 (US-D3)")
    void outsiderCanStillProcess() {
        IssueView created = register("전원이 처리하는 이슈", null);

        // 이슈 게시판은 전사 공개이고 처리는 현장의 일이다 — 여기 관문을 걸면
        // 팀원이 자기가 맡을 이슈를 처리할 수 없다
        IssueView processed = issueCommandService.process(OUTSIDER_ID, created.id(),
                IssueEditCommand.ofProcess(IssueStatus.IN_PROGRESS, OUTSIDER_ID),
                created.version());

        assertThat(processed.statusCode()).isEqualTo(IssueStatus.IN_PROGRESS);
        assertThat(processed.assignee().id()).isEqualTo(OUTSIDER_ID);
    }

    @Test
    @DisplayName("D3-5 — 본문은 빈 문자열로 지우고 null은 그대로다")
    void contentIsClearedByBlankAndKeptByNull() {
        IssueView created = issueCommandService.register(REPORTER_ID, new IssueCommand(
                siteOf("본문 규약 계약", null), IssueType.INQUIRY, "본문 규약", "처음 본문"));
        assertThat(created.content()).isEqualTo("처음 본문");

        // null = 그대로 (제목만 바꾼다)
        IssueView kept = issueCommandService.process(REPORTER_ID, created.id(),
                new IssueEditCommand(null, null, null, "제목만 바꿈", null), created.version());
        assertThat(kept.content()).isEqualTo("처음 본문");

        // 빈 문자열 = 지운다
        IssueView cleared = issueCommandService.process(REPORTER_ID, created.id(),
                new IssueEditCommand(null, null, null, null, "  "), kept.version());
        assertThat(cleared.content()).isNull();
    }

    @Test
    @DisplayName("D3-5 — 다섯 칸이 전부 비어 있으면 400이다 (바꿀 것이 없다)")
    void emptyEditIsRejected() {
        IssueView created = register("빈 요청 대상", null);

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> issueCommandService.process(REPORTER_ID, created.id(),
                        new IssueEditCommand(null, null, null, null, null), created.version()));
    }

    @Test
    @DisplayName("D3-5 — 제목을 공백으로 비우려 하면 400이다 (null과 다르다)")
    void blankTitleIsRejected() {
        IssueView created = register("제목은 비울 수 없다", null);

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> issueCommandService.process(REPORTER_ID, created.id(),
                        new IssueEditCommand(null, null, null, "   ", null), created.version()));
    }

    // --- D3-6 삭제 -----------------------------------------------------------

    @Test
    @DisplayName("D3-6 — 삭제하면 목록·상세·계약 요약 전부에서 빠진다 (행은 남는다)")
    void deleteRemovesItFromEveryReadPath() {
        long siteId = siteOf("삭제 확인 계약", null);
        IssueView created = issueCommandService.register(
                REPORTER_ID, new IssueCommand(siteId, IssueType.INCIDENT, "지워질 이슈", null));
        long contractId = created.contractId();
        assertThat(issuesOf(siteId)).contains(created.id());
        assertThat(openCountOf(contractId)).isEqualTo(1);

        issueCommandService.delete(REPORTER_ID, created.id(), created.version());

        // 세 경로가 같은 답을 내야 한다 — 하나만 걸러지면 화면끼리 어긋난다
        assertThat(issuesOf(siteId)).doesNotContain(created.id());
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> issueQueryService.getIssue(created.id()));
        assertThat(openCountOf(contractId)).isZero();
    }

    @Test
    @DisplayName("D3-6 — 삭제된 이슈는 수정도 재삭제도 404다 (삭제와 부재가 같은 답)")
    void deletedIssueIsGoneForWritesToo() {
        IssueView created = register("두 번 지울 수 없는 이슈", null);
        issueCommandService.delete(REPORTER_ID, created.id(), created.version());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> issueCommandService.process(REPORTER_ID, created.id(),
                        new IssueEditCommand(null, null, null, "부활 시도", null), 1L));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> issueCommandService.delete(REPORTER_ID, created.id(), 1L));
    }

    @Test
    @DisplayName("D3-6 — 권한 없으면 403이고, version이 어긋나면 409다")
    void deleteIsGuardedAndVersioned() {
        IssueView created = register("삭제 관문 대상", null);

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> issueCommandService.delete(
                        OUTSIDER_ID, created.id(), created.version()));
        assertThatExceptionOfType(StaleVersionException.class)
                .isThrownBy(() -> issueCommandService.delete(
                        REPORTER_ID, created.id(), created.version() + 99));

        // 둘 다 아무것도 지우지 않았다
        assertThat(issueQueryService.findIssue(created.id())).isPresent();
    }

    // --- D3-7 코멘트 수정·삭제 -----------------------------------------------

    @Test
    @DisplayName("D3-7 — 작성자는 자기 코멘트를 고치고, 고쳐진 사실이 남는다")
    void authorEditsOwnComment() {
        IssueView created = register("코멘트 수정 대상", null);
        CommentView added =
                issueCommandService.addComment(REPORTER_ID, created.id(), "처음 적은 내용");
        assertThat(added.updatedAt()).isNull();

        CommentView edited =
                issueCommandService.editComment(REPORTER_ID, added.id(), "고쳐 적은 내용");

        assertThat(edited.content()).isEqualTo("고쳐 적은 내용");
        // append-only는 폐기됐지만 수정 흔적을 지우지는 않는다
        assertThat(edited.updatedAt()).isNotNull();
        // 작성 시각은 수정으로 움직이지 않는다. **근접 비교인 이유가 실측으로 두 단이다**
        // (2026-08-26): ①`save()`가 돌려준 값은 메모리의 나노초이고 다시 읽은 값은
        // PostgreSQL `timestamp with time zone`의 마이크로초다 ②그리고 그 변환은
        // 버림이 아니라 **반올림**이라 `truncatedTo(MICROS)`로도 끝자리가 갈린다
        // (...479125400Z를 자르면 479125인데 DB에는 479126이 들어 있다).
        // 같은 시각인지를 묻는 단정이므로 정밀도 경계를 넘는 비교로 둔다
        assertThat(edited.createdAt())
                .isCloseTo(added.createdAt(), within(1, ChronoUnit.MILLIS));
        assertThat(edited.updatedAt()).isAfterOrEqualTo(edited.createdAt());
        // 조회 경로도 같은 것을 본다
        assertThat(issueQueryService.getIssue(created.id()).comments())
                .singleElement()
                .satisfies(comment -> {
                    assertThat(comment.content()).isEqualTo("고쳐 적은 내용");
                    assertThat(comment.updatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("D3-7 — 작성자는 자기 코멘트를 지운다 (행이 사라진다)")
    void authorDeletesOwnComment() {
        IssueView created = register("코멘트 삭제 대상", null);
        CommentView added = issueCommandService.addComment(REPORTER_ID, created.id(), "지울 코멘트");

        issueCommandService.deleteComment(REPORTER_ID, added.id());

        assertThat(issueQueryService.getIssue(created.id()).comments()).isEmpty();
    }

    @Test
    @DisplayName("D3-7 — 남의 코멘트는 관리자여도 수정·삭제할 수 없다 (범위가 작성자다)")
    void othersCommentIsUntouchableEvenForManager() {
        IssueView created = register("남의 코멘트", null);
        CommentView mine = issueCommandService.addComment(REPORTER_ID, created.id(), "내가 적었다");

        // "계약 관리" 플래그는 이슈 정정의 근거이고 코멘트에는 미치지 않는다 —
        // 사용자 결정이 범위를 "본인 것만"으로 좁혔다
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> issueCommandService.editComment(
                        MANAGER_ID, mine.id(), "관리자가 고친다"));
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> issueCommandService.deleteComment(MANAGER_ID, mine.id()));

        assertThat(issueQueryService.getIssue(created.id()).comments())
                .singleElement()
                .satisfies(comment -> assertThat(comment.content()).isEqualTo("내가 적었다"));
    }

    @Test
    @DisplayName("D3-7 — 코멘트를 비우려 하면 400이고, 없는 코멘트는 404다")
    void commentEditValidatesAndHides() {
        IssueView created = register("코멘트 검증", null);
        CommentView added = issueCommandService.addComment(REPORTER_ID, created.id(), "내용");

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> issueCommandService.editComment(REPORTER_ID, added.id(), "  "));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> issueCommandService.editComment(REPORTER_ID, 999_999L, "내용"));
    }

    @Test
    @DisplayName("D3-7 — 이슈가 삭제되면 그 코멘트도 손댈 수 없다 (404)")
    void commentsOfDeletedIssueAreGone() {
        IssueView created = register("삭제된 이슈의 코멘트", null);
        CommentView added = issueCommandService.addComment(REPORTER_ID, created.id(), "내용");
        issueCommandService.delete(REPORTER_ID, created.id(), created.version());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> issueCommandService.editComment(REPORTER_ID, added.id(), "수정"));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> issueCommandService.deleteComment(REPORTER_ID, added.id()));
    }

    // --- 픽스처 --------------------------------------------------------------

    private static Person person(long id, String name, long groupId) {
        return Person.of(id, name, TEAM_ID, GRADE_ID, groupId, 1.0, true, false, true);
    }

    /** 등록자는 항상 REPORTER_ID다 — 정정 권한의 기준점이 그 값이다. */
    private IssueView register(String title, Long engineerId) {
        return issueCommandService.register(REPORTER_ID, new IssueCommand(
                siteOf(title + " 계약", engineerId), IssueType.INCIDENT, title, null));
    }

    /** 계약·사이트를 하나씩 세우고 사이트 id를 준다 — 이슈는 사이트에 붙는다. */
    private long siteOf(String contractName, Long engineerId) {
        ContractDetail contract = contractCommandService.create(MANAGER_ID, new ContractCommand(
                "㈜가온아이", contractName, ContractStatus.ACTIVE, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 61320000L, 5110000L,
                MANAGER_ID, "검색엔진", "그룹웨어", "월 1회", null));
        SiteView site = contractCommandService.addSite(MANAGER_ID, contract.id(),
                new SiteCommand("가천대길병원", SiteChannel.OEM, null, engineerId, List.of()));

        return site.id();
    }

    private List<Long> issuesOf(long siteId) {
        return issueQueryService
                .search(new IssueQuery(null, null, siteId, null, false, null), page())
                .stream()
                .map(IssueView::id)
                .toList();
    }

    /** 계약 상세의 이슈 요약 — soft 삭제가 이 집계에서도 빠져야 한다. */
    private long openCountOf(long contractId) {
        return maintenanceQueryService.getContract(contractId).issueCountByStatus().values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    private static Pageable page() {
        return PageRequest.of(0, 100);
    }
}
