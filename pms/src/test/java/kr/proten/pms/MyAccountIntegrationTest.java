package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import kr.proten.pms.audit.AuditQueryService;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.auth.service.AuthService;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.AccountPort;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.PersonService;
import kr.proten.pms.person.service.dto.AccountView;
import kr.proten.pms.person.service.dto.UpdateProfileCommand;
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
 * 내 계정 관통 (US-H1 — H1-1 상세 · H1-2 프로필 · H1-3 비밀번호).
 *
 * <p><b>두 모듈이 한 트랜잭션에서 움직이는지</b>가 여기서만 보인다: 이름은 person의
 * {@code people} 행이고 email·phone은 auth의 {@code users} 행이라, 프로필 수정 한 번이
 * 두 표를 함께 바꾸거나 함께 두어야 한다.
 *
 * <p>전용 id 블록(10xx)과 전용 직급·조직 노드를 쓴다 — 공유 픽스처 행을 <b>바꾸지</b>
 * 않는 것이 규칙이다(2026-08-24 실측). 처음에 6xx를 골랐다가
 * {@code OrgAdminWritesIntegrationTest}와 충돌해 낙관적 락 실패로 드러났다 —
 * 100단위 블록도 비어 있는지 실측하고 고를 일이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyAccountIntegrationTest extends PostgresTestBase {
    private static final long GROUP_ID = 1001L;
    private static final long ME_ID = 1001L;
    private static final long OTHER_ID = 1002L;
    private static final long TEAM_ID = 1021L;
    private static final long GRADE_ID = 1031L;

    private static final String MY_EMAIL = "h1.me@proten.co.kr";
    private static final String OTHER_EMAIL = "h1.other@proten.co.kr";
    private static final String INITIAL_PASSWORD = "proten1!";

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
    private AuthService authService;
    @Autowired
    private AccountPort accountPort;
    @Autowired
    private AuditQueryService auditQueryService;

    @BeforeAll
    void seedFixture() {
        orgUnitRepository.saveAll(PersonFixtures.orgUnits());
        orgUnitRepository.save(OrgUnit.of(TEAM_ID, PersonFixtures.COMPANY_ID, "H1계정팀"));
        gradeRepository.save(Grade.of(GRADE_ID, "H1선임", 1.0));
        permissionGroupRepository.save(
                PersonFixtures.group(GROUP_ID, "H1팀원", VisibilityScope.TEAM));
        personRepository.saveAll(List.of(
                Person.of(ME_ID, "H1나", TEAM_ID, GRADE_ID, GROUP_ID, 1.0, true, false, true),
                Person.of(OTHER_ID, "H1남", TEAM_ID, GRADE_ID, GROUP_ID, 1.0, true, false, true)));
        accountPort.createInitialAccount(ME_ID, MY_EMAIL);
        accountPort.createInitialAccount(OTHER_ID, OTHER_EMAIL);
    }

    @Test
    @DisplayName("H1-2 — 이름을 그대로 두고 email만 바꿔도 감사 행이 남는다")
    void emailOnlyChangeIsStillAudited() {
        // Given — person 스냅샷만 보면 diff가 비어 감사 행이 0건이었다(2026-08-25 리뷰)
        long before = auditRowsForMe();

        // When
        personService.updateProfile(ME_ID,
                new UpdateProfileCommand("H1나", "h1.audited@proten.co.kr", null));

        // Then — 로그인 ID 변경이 흔적 없이 일어나면 안 된다
        assertThat(auditRowsForMe()).isGreaterThan(before);
        AuditRecord row = auditQueryService.findAll(PageRequest.of(0, 500)).stream()
                .filter(record -> "Person".equals(record.entityType()))
                .filter(record -> record.entityId() != null && record.entityId() == ME_ID)
                .filter(record -> record.after().containsKey("email"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("email 변경 감사 행이 없다"));
        assertThat(row.before()).containsEntry("email", MY_EMAIL);
        assertThat(row.after()).containsEntry("email", "h1.audited@proten.co.kr");

        personService.updateProfile(ME_ID, new UpdateProfileCommand("H1나", MY_EMAIL, null));
    }

    private long auditRowsForMe() {
        return auditQueryService.findAll(PageRequest.of(0, 500)).stream()
                .filter(record -> "Person".equals(record.entityType()))
                .filter(record -> record.entityId() != null && record.entityId() == ME_ID)
                .count();
    }

    @Test
    @DisplayName("H1-1 — 내 계정 상세는 이름(person)과 연락처(auth)를 함께 준다")
    void accountJoinsBothModules() {
        // When
        AccountView account = personService.myAccount(ME_ID);

        // Then
        assertThat(account.id()).isEqualTo(ME_ID);
        assertThat(account.name()).isEqualTo("H1나");
        assertThat(account.email()).isEqualTo(MY_EMAIL);
        // 시드 계정은 전화번호가 없다 — 없는 것이 정상 상태다
        assertThat(account.phone()).isNull();
    }

    @Test
    @DisplayName("H1-2 — 프로필 수정은 두 표를 함께 바꾼다 (이름·연락처)")
    void profileUpdateWritesBothTables() {
        // When
        personService.updateProfile(ME_ID,
                new UpdateProfileCommand("H1바뀐이름", "h1.changed@proten.co.kr", "010-1234-5678"));

        // Then — 다시 읽어도 둘 다 바뀌어 있다(같은 트랜잭션에서 커밋됐다)
        AccountView account = personService.myAccount(ME_ID);
        assertThat(account.name()).isEqualTo("H1바뀐이름");
        assertThat(account.email()).isEqualTo("h1.changed@proten.co.kr");
        assertThat(account.phone()).isEqualTo("010-1234-5678");
        // person 표도 실제로 바뀌었다
        assertThat(personRepository.findByIdAndActiveTrue(ME_ID).orElseThrow().getName())
                .isEqualTo("H1바뀐이름");

        // 되돌린다 — 뒤 테스트가 원래 email에 기댄다
        personService.updateProfile(ME_ID, new UpdateProfileCommand("H1나", MY_EMAIL, null));
    }

    @Test
    @DisplayName("H1-2 — 내 email을 그대로 두고 이름만 바꾸는 것은 409가 아니다")
    void keepingMyOwnEmailIsNotADuplicate() {
        // When · Then — "아무도 안 쓴다"로 검사하면 자기 email 때문에 409가 난다
        personService.updateProfile(ME_ID, new UpdateProfileCommand("H1나2", MY_EMAIL, null));
        assertThat(personService.myAccount(ME_ID).name()).isEqualTo("H1나2");

        personService.updateProfile(ME_ID, new UpdateProfileCommand("H1나", MY_EMAIL, null));
    }

    @Test
    @DisplayName("H1-2 — 남이 쓰는 email로 바꾸면 409이고 이름도 바뀌지 않는다")
    void takenEmailIsRejectedAtomically() {
        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> personService.updateProfile(ME_ID,
                        new UpdateProfileCommand("H1실패", OTHER_EMAIL, null)))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.DUPLICATE_EMAIL));
        // 중복 검사가 변경보다 앞이라는 것이 이 단정이다
        assertThat(personService.myAccount(ME_ID).name()).isEqualTo("H1나");
    }

    @Test
    @DisplayName("H1-3 — 현재 비밀번호가 맞으면 바뀌고 새 비밀번호로 로그인된다")
    void passwordChangeTakesEffectOnLogin() {
        // When
        authService.changePassword(ME_ID, INITIAL_PASSWORD, "newpass123");

        // Then — 로그인이 실제로 새 값을 받는다(해시가 저장됐다)
        assertThat(authService.login(MY_EMAIL, "newpass123")).isNotNull();

        // 되돌린다
        authService.changePassword(ME_ID, "newpass123", INITIAL_PASSWORD);
    }

    @Test
    @DisplayName("H1-3 — 불일치·형식 오류가 문구와 필드까지 같은 400이다")
    void wrongCurrentAndShortNewConvergeOn400() {
        // When — 세 갈래를 모두 만든다
        ValidationException wrongCurrent = catchThrowableOfType(ValidationException.class,
                () -> authService.changePassword(ME_ID, "틀린값", "newpass123"));
        ValidationException shortNew = catchThrowableOfType(ValidationException.class,
                () -> authService.changePassword(ME_ID, INITIAL_PASSWORD, "short7"));
        ValidationException noAccount = catchThrowableOfType(ValidationException.class,
                () -> authService.changePassword(999999L, "아무거나", "newpass123"));

        // Then — <b>상태 코드만 같으면 부족하다</b>: message·field가 갈리면 공격자가
        //        비밀번호를 바꾸지 않고도 현재 비밀번호를 맞혔는지 알 수 있다
        //        (2026-08-25 리뷰가 잡은 실제 누출 — 이 단정이 그것을 잠근다)
        assertThat(wrongCurrent.getMessage()).isEqualTo(shortNew.getMessage());
        assertThat(wrongCurrent.getMessage()).isEqualTo(noAccount.getMessage());
        assertThat(wrongCurrent.field()).isEqualTo(shortNew.field());
        assertThat(wrongCurrent.field()).isEqualTo(noAccount.field());
        // 셋 다 실패했으므로 원래 비밀번호가 그대로다
        assertThat(authService.login(MY_EMAIL, INITIAL_PASSWORD)).isNotNull();
    }
}
