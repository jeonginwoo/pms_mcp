package kr.proten.pms.person.service.impl;

import java.util.List;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.NotImplementedException;
import kr.proten.pms.common.exception.UnprocessableException;
import java.util.Map;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.AccountContact;
import kr.proten.pms.person.AccountPort;
import kr.proten.pms.person.OrgVisibility;
import kr.proten.pms.person.OrgVisibilityService;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.PersonService;
import kr.proten.pms.person.AssignmentCountPort;
import kr.proten.pms.person.AssignmentReleasePort;
import kr.proten.pms.person.LiveAssignment;
import kr.proten.pms.person.service.dto.AccountView;
import kr.proten.pms.person.service.dto.CreatePersonCommand;
import kr.proten.pms.person.service.dto.MeView;
import kr.proten.pms.person.service.dto.PersonDeactivateResult;
import kr.proten.pms.person.service.dto.PersonSummary;
import kr.proten.pms.person.service.dto.OrgUnitMoveResult;
import kr.proten.pms.person.service.dto.UpdatePersonCommand;
import kr.proten.pms.person.service.dto.UpdateProfileCommand;
import kr.proten.pms.person.service.entity.PermissionGroup;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.impl.requester.Requester;
import kr.proten.pms.person.service.impl.requester.RequesterResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인력 유스케이스 — AC E1-1 · E2-1~E2-5 · H1-1.
 *
 * 판정 축이 둘이고 그 경계가 이 클래스의 규칙이다: **조회는 가시성**(범위 밖은
 * 403이 아니라 404로 은닉 — 상위 PRD §4-4), **쓰기는 권한 그룹의
 * "사용자/조직/권한 관리" 플래그**(기본 그룹 중 관리자만 — §4-3).
 *
 * 쓰기의 검사 순서는 권한(403) → 입력·참조(400·422) → 중복(409)이다: 관리 권한이
 * 없는 호출자에게 어떤 id·email이 존재하는지 알려 주지 않으려면 권한이 가장 앞이어야
 * 한다. 조회는 반대로 대상 존재 여부를 먼저 확인하고 가시성으로 다시 거른다 —
 * 두 사유가 같은 404로 수렴해야 하기 때문이다.
 */
@Service
@Transactional
public class PersonServiceImpl implements PersonService {
    // 부록 B 기본값 — 월 가용 M/M 1.0 · 가동률 집계 대상
    private static final double DEFAULT_CAPACITY = 1.0;

    private final PersonRepository personRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final GradeRepository gradeRepository;
    private final PermissionGroupRepository permissionGroupRepository;
    private final AccountPort accountPort;
    private final OrgVisibilityService orgVisibilityService;
    private final OrgManagePermission orgManagePermission;
    private final RequesterResolver requesterResolver;
    private final PersonRefFactory personRefFactory;
    private final PersonAuditRecorder personAuditRecorder;
    private final AssignmentCountPort assignmentCountPort;
    private final AssignmentReleasePort assignmentReleasePort;

    public PersonServiceImpl(
            PersonRepository personRepository,
            OrgUnitRepository orgUnitRepository,
            GradeRepository gradeRepository,
            PermissionGroupRepository permissionGroupRepository,
            AccountPort accountPort,
            OrgVisibilityService orgVisibilityService,
            OrgManagePermission orgManagePermission,
            RequesterResolver requesterResolver,
            PersonRefFactory personRefFactory,
            PersonAuditRecorder personAuditRecorder,
            AssignmentCountPort assignmentCountPort,
            AssignmentReleasePort assignmentReleasePort) {
        this.personRepository = personRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.gradeRepository = gradeRepository;
        this.permissionGroupRepository = permissionGroupRepository;
        this.accountPort = accountPort;
        this.orgVisibilityService = orgVisibilityService;
        this.orgManagePermission = orgManagePermission;
        this.requesterResolver = requesterResolver;
        this.personRefFactory = personRefFactory;
        this.personAuditRecorder = personAuditRecorder;
        this.assignmentCountPort = assignmentCountPort;
        this.assignmentReleasePort = assignmentReleasePort;
    }

    /** 가시성 범위 내 인원 목록 — 시스템 계정·비활성 인원은 제외한다. */
    @Transactional(readOnly = true)
    public List<PersonSummary> listVisible(long callerPersonId) {
        OrgVisibility visibility = orgVisibilityService.visibilityOf(callerPersonId);

        if (visibility.unrestricted()) {
            return personRefFactory.toSummaries(
                    personRepository.findByActiveTrueAndSystemFalseOrderByIdAsc());
        }

        return personRefFactory.toSummaries(
                personRepository.findByIdInAndActiveTrueAndSystemFalseOrderByIdAsc(
                        visibility.visiblePersonIds()));
    }

    /**
     * 인원 단건 조회.
     * 노출 대상이 아닌 인원(부재·시스템 계정·비활성)과 가시성 밖 인원은 같은
     * 404다 — 사유가 응답으로 새면 존재 자체가 드러난다.
     */
    @Transactional(readOnly = true)
    public PersonSummary getPerson(long callerPersonId, long personId) {
        Person target = personRepository.findByIdAndActiveTrue(personId)
                .filter(person -> !person.isSystem())
                .orElseThrow(NotFoundException::new);

        if (!orgVisibilityService.visibilityOf(callerPersonId).canView(personId)) {
            throw new NotFoundException();
        }

        return personRefFactory.toSummary(target);
    }

    /**
     * 화자 자신의 신원과 권한 그룹 플래그 (AC H1-1).
     * 신원은 인력 조회와 같은 표현(PersonRef)을 쓴다 — 이름·조직·직급 해석 규칙이
     * 갈라지지 않게 하려는 것이다(PersonRefFactory 주석과 같은 근거).
     */
    @Transactional(readOnly = true)
    public MeView me(long callerPersonId) {
        Requester requester = requesterResolver.resolve(callerPersonId);
        PersonRef identity = personRefFactory.toRef(requester.person());
        PermissionGroup group = requester.group();

        return new MeView(
                identity.id(),
                identity.name(),
                identity.orgUnit(),
                identity.grade(),
                group.getName(),
                group.getVisibilityScope().name(),
                group.isCreateProject(),
                group.isManageContracts(),
                group.isManageAllProjects(),
                group.isManageOrg());
    }

    /** 내 계정 상세 (AC H1-1) — 연락처는 auth가 갖고 있어 포트로 받아 온다. */
    @Override
    @Transactional(readOnly = true)
    public AccountView myAccount(long callerPersonId) {
        Person me = personRepository.findByIdAndActiveTrue(callerPersonId)
                .orElseThrow(NotFoundException::new);
        AccountContact contact = accountPort.contactOf(callerPersonId)
                // 실측상 44명 전원이 계정을 갖는다(시드가 시스템 계정에도 넣는다) —
                // 빈 값은 정상 상태가 아니라 데이터 이상이다. 다만 <b>조회</b>는 그것
                // 때문에 화면을 막지 않는다: 쓰기(updateContact)가 404로 거절한다
                .orElse(new AccountContact(null, null));

        return new AccountView(me.getId(), me.getName(), contact.email(), contact.phone());
    }

    /**
     * 내 프로필 수정 (AC H1-2) — 이름은 person, email·phone은 auth다.
     *
     * <p>순서가 <b>중복 검사 → 변경</b>인 것은 다른 쓰기와 같다: 409를 받을 요청이
     * 이름을 먼저 바꿔 두면 롤백에 기대게 된다. 포트 구현이 호출자의 트랜잭션에
     * 참여하므로 둘은 함께 커밋되거나 함께 사라진다.
     */
    @Override
    @Transactional
    public AccountView updateProfile(long callerPersonId, UpdateProfileCommand command) {
        Person me = personRepository.findByIdAndActiveTrue(callerPersonId)
                .orElseThrow(NotFoundException::new);
        String name = required(command.name(), "이름은 필수입니다", "name");
        String email = required(command.email(), "이메일은 필수입니다", "email");
        String phone = blankToNull(command.phone());

        if (accountPort.emailTakenByOther(callerPersonId, email)) {
            throw new ConflictException(ErrorCode.DUPLICATE_EMAIL, "이미 사용 중인 이메일입니다");
        }

        // 연락처는 auth 행이라 person 스냅샷에 안 들어온다 — 따로 뜬다
        AccountContact contactBefore = accountPort.contactOf(callerPersonId)
                .orElse(new AccountContact(null, null));
        AccountContact contactAfter = new AccountContact(email, phone);
        Map<String, Object> before = personAuditRecorder.snapshot(me);
        me.rename(name);
        Person saved = personRepository.saveAndFlush(me);
        accountPort.updateContact(callerPersonId, email, phone);
        personAuditRecorder.personChanged(callerPersonId, saved, before);
        // 이름이 안 바뀌어도 email만 바뀌면 여기서 행이 남는다 — 로그인 ID
        // 변경이 흔적 없이 일어나지 않게 한다(2026-08-25 리뷰)
        personAuditRecorder.contactChanged(
                callerPersonId, saved, contactBefore, contactAfter);

        return myAccountOf(saved, email, phone);
    }

    private static AccountView myAccountOf(Person person, String email, String phone) {
        return new AccountView(person.getId(), person.getName(), email, phone);
    }

    private static String required(String value, String message, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message, field);
        }

        return value.trim();
    }

    /** 빈 문자열은 "없음"이다 — 전화번호는 없는 것이 정상 상태다. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 인원 + 로그인 계정을 한 트랜잭션에서 만든다 (AC E2-1).
     * 둘을 쪼개지 않는 이유: 계정 없는 인원은 로그인할 수 없고, 인원 없는 계정은
     * 화자가 될 수 없다 — 절반만 만들어진 상태가 의미를 갖지 않는다.
     *
     * 계정 생성은 `AccountPort`(auth 구현)에 맡긴다 — 초기 비밀번호·해시 방식은
     * person이 알 일이 아니고, 같은 트랜잭션에 참여하므로 원자성은 그대로다.
     */
    public PersonSummary create(long callerPersonId, CreatePersonCommand command) {
        orgManagePermission.require(callerPersonId);
        requireText(command.name(), "name");
        requireText(command.email(), "email");
        requireReferences(command.orgUnitId(), command.gradeId(), command.groupId());
        requireUniqueEmail(command.email());

        Person saved = personRepository.save(Person.of(
                personRepository.nextId(),
                command.name().trim(),
                command.orgUnitId(),
                command.gradeId(),
                command.groupId(),
                DEFAULT_CAPACITY,
                true,
                false,
                true));
        accountPort.createInitialAccount(saved.getId(), command.email());
        personAuditRecorder.personCreated(callerPersonId, saved);

        return personRefFactory.toSummary(saved);
    }

    /**
     * 인력 수정 — **골격만 있고 로직은 아직 없다** (2026-08-22).
     *
     * 이미 정해져 있는 것: 검사 순서는 등록과 같고(권한 → 참조 → 중복), 시스템 계정은
     * 수정도 `422 IMMUTABLE_ACCOUNT`이며(E2-5), 변경은 `UPDATE` 감사 1행이다.
     * 그룹 변경이 여기 있는 이유는 그룹이 사람의 속성이기 때문이다(2026-08-09 ⑦).
     *
     * **권한 판정은 골격 단계에서도 실제로 한다** — 관리 플래그 없는 호출자가 501을
     * 받으면 "이 경로가 존재한다"와 "곧 열린다"를 알게 되고, 구현이 들어오는 순간
     * 403이 뒤늦게 생긴다. 없는 것은 로직이지 권한이 아니다.
     *
     */
    public PersonSummary update(long callerPersonId, UpdatePersonCommand command) {
        orgManagePermission.require(callerPersonId);

        Person target = requireEditable(command.personId());
        // 2026-08-24 결함 수정: 요청의 version을 받아 두고 검사하지 않아 마지막 쓰기가
        // 조용히 이기고 있었다. `UpdatePersonCommand`의 javadoc은 처음부터 409를 적고 있었다
        target.requireVersion(command.version());
        requireReferences(command.orgUnitId(), command.gradeId(), command.groupId());

        // 바꾸기 직전에 떠 둔다 — 바뀐 필드만 이력에 남고, 바뀐 것이 없으면 행도 없다
        Map<String, Object> before = personAuditRecorder.snapshot(target);
        target.update(
                requireName(command.name()),
                command.orgUnitId(),
                command.gradeId(),
                command.groupId(),
                command.billable());
        // flush 해야 응답의 version이 커밋 뒤 값이 된다 — 안 하면 화면이 옛 version으로
        // 다시 저장하려다 409를 받는다(project·maintenance가 같은 이유로 saveAndFlush)
        Person saved = personRepository.saveAndFlush(target);
        personAuditRecorder.personChanged(callerPersonId, saved, before);
        return personRefFactory.toSummary(saved);
    }

    /**
     * 소속 조직 이동 — **골격만 있고 로직은 아직 없다** (2026-08-22).
     *
     * 이미 정해져 있는 것: 진행 중 배정이 있어도 **허용**한다(E1-2) — 막으면 실제
     * 조직 개편을 시스템이 거부하게 된다. 과거 집계는 시점을 보존하지 않으므로
     * 현재 소속 기준으로 다시 계산된다(같은 AC). 감사 action은 `UPDATE`다
     * (STATE_CHANGE는 §5 프로젝트 상태 전이 전용 — v2.1 정리).
     *
     * 권한 판정은 여기서도 먼저 한다(위 update와 같은 이유).
     *
     * 경고는 **응답 본문에 담는다**(2026-08-24 사용자 결정): 이동을 막지 않으므로 오류로
     * 낼 수 없고, 알림(EPIC F)으로 보내면 지금 화면에서 조직을 개편하는 사람이 그것을
     * 보지 못한다. 진행 중 배정 건수는 {@link kr.proten.pms.person.AssignmentCountPort}로
     * 묻는다 — person이 project를 직접 부르면 순환이다.
     */
    public OrgUnitMoveResult moveOrgUnit(long callerPersonId, long personId, long orgUnitId) {
        orgManagePermission.require(callerPersonId);

        Person target = requireEditable(personId);
        requireExists(orgUnitRepository.existsById(orgUnitId), "조직", orgUnitId);

        // 배정 건수는 엔티티를 바꾸기 **전에** 묻는다 — 더러워진 세션에 질의하면 JPA가
        // 먼저 flush 해 version이 한 유스케이스에서 두 번 오른다(conventions §4)
        long activeAssignments = assignmentCountPort.countActiveAssignments(personId);
        Map<String, Object> before = personAuditRecorder.snapshot(target);
        target.moveTo(orgUnitId);
        Person saved = personRepository.saveAndFlush(target);
        // 소속 이동도 UPDATE다 — STATE_CHANGE는 §5 프로젝트 상태 전이 전용(v2.1 정리)
        personAuditRecorder.personChanged(callerPersonId, saved, before);
        return OrgUnitMoveResult.of(personRefFactory.toSummary(saved), activeAssignments);
    }

    /**
     * 인원 비활성 (AC E2-3 + PRD-pms §12 ③ — 2026-08-26 확장).
     *
     * <p>세 가지가 한 트랜잭션에서 일어난다: <b>PM 판정 → 참여자 배정 자동 종료 →
     * 비활성</b>. 순서가 규칙이다 — PM으로 물려 있으면 <b>아무것도 하지 않고 409</b>로
     * 거절한다. 그대로 비활성하면 프로젝트가 PM 공석이 되는데, A6-5는 "진행 중 프로젝트당
     * role=PM 정확히 1행"을 요구하고 PM 교체(A6)는 <b>사람을 지정해야</b> 성립하므로
     * 시스템이 대신 고를 수 없다. 그래서 §12 ③이 "교체를 요구한다"로 정했다.
     *
     * <p>참여자 배정을 종료하는 이유는 반대다: 대체자가 필요하면 새로 배정하면 되고,
     * 살려 두면 <b>퇴사자가 가동률 모집단에 남는다</b>(C1-4).
     *
     * <p>판정은 여기서 하고 실행은 project가 한다 — 두 반쪽이 갈린 자리와 이유는
     * {@link kr.proten.pms.person.AssignmentReleasePort}에 있다.
     */
    public PersonDeactivateResult deactivate(long callerPersonId, long personId) {
        orgManagePermission.require(callerPersonId);

        Person target = personRepository.findByIdAndActiveTrue(personId)
                .orElseThrow(NotFoundException::new);
        requireDeactivatable(callerPersonId, target);

        List<LiveAssignment> live = assignmentReleasePort.findLiveAssignments(personId);
        requireNoManagerAssignments(live);

        // 배정을 먼저 끊는다 — 비활성 뒤에 끊으면 그 사이에 실패했을 때 "비활성인데
        // 배정이 살아 있는" 상태가 남는다(한 트랜잭션이라 롤백되지만, 읽는 순서가
        // 곧 불변식의 순서다)
        assignmentReleasePort.closeParticipantAssignments(callerPersonId, personId);

        target.deactivate();
        Person saved = personRepository.saveAndFlush(target);
        personAuditRecorder.personDeactivated(callerPersonId, saved);

        return PersonDeactivateResult.of(personRefFactory.toSummary(saved), live);
    }

    /**
     * PM으로 물린 프로젝트가 있으면 409 IN_USE (§12 ③ — "교체를 요구한다").
     *
     * <p>목록을 <b>메시지에 담는다</b>: §7 오류 봉투는 {@code code·message·field·traceId}뿐이라
     * 구조화된 목록을 실을 자리가 없고, E3-3의 조직 삭제 거절이 이미 같은 모양으로 답한다.
     * 다만 <b>이름을 셋까지만 적는다</b> — 실측상 한 사람이 PM인 진행 중 프로젝트가
     * 29건까지 나와서(2026-08-26), 전부 적으면 메시지가 화면을 넘긴다.
     */
    private void requireNoManagerAssignments(List<LiveAssignment> live) {
        List<String> managed = live.stream()
                .filter(LiveAssignment::manager)
                .map(LiveAssignment::projectName)
                .toList();

        if (managed.isEmpty()) {
            return;
        }

        throw new ConflictException(ErrorCode.IN_USE,
                "PM으로 지정된 진행 중 프로젝트 %d건(%s)이 있습니다 — PM을 교체한 뒤 삭제하세요"
                        .formatted(managed.size(), summarize(managed)));
    }

    /** 앞의 셋만 적고 나머지는 센다 — 목록이 길어도 문구가 읽히게. */
    private String summarize(List<String> names) {
        if (names.size() <= 3) {
            return String.join(", ", names);
        }

        return "%s 외 %d건".formatted(String.join(", ", names.subList(0, 3)), names.size() - 3);
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("필수 입력값입니다", field);
        }
    }

    /** 조직·직급·권한 그룹 참조 검증 (A1-3과 같은 의미론의 422). */
    private void requireReferences(Long orgUnitId, Long gradeId, Long groupId) {
        requireExists(orgUnitRepository.existsById(orgUnitId), "조직", orgUnitId);
        requireExists(gradeRepository.existsById(gradeId), "직급", gradeId);
        requireExists(permissionGroupRepository.existsById(groupId), "권한 그룹", groupId);
    }

    private void requireExists(boolean exists, String label, Long id) {
        if (!exists) {
            throw new UnprocessableException(ErrorCode.REF_NOT_FOUND, "없는 %s입니다: %s".formatted(label, id));
        }
    }

    /** 로그인 ID 중복 (AC E2-1 — H1-2와 같은 코드). */
    private void requireUniqueEmail(String email) {
        if (accountPort.emailTaken(email)) {
            throw new ConflictException(ErrorCode.DUPLICATE_EMAIL, "이미 사용 중인 이메일입니다");
        }
    }

    /**
     * 시스템 계정은 감사 actor·수습 주체라 비활성할 수 없다 (AC E2-5).
     *
     * ASSUMPTION: 본인 계정도 막는다 — 명세에 없지만 관리 권한자가 스스로를 비활성하면
     * 그 조직에 관리자가 없는 상태가 만들어질 수 있다(관리자 그룹 systemFixed와 같은
     * 자기 잠금 방지 원리 — 상위 PRD §4-3).
     */
    /**
     * 수정·이동의 공통 관문 (AC E2-5) — 시스템 계정은 `422 IMMUTABLE_ACCOUNT`다.
     *
     * <p>비활성 인원은 404로 막는다: 목록에서 빠진 사람을 편집할 경로가 열려 있으면
     * "삭제했는데 여전히 고칠 수 있다"가 되고, 되살리는 입구는 §7에 없다.
     *
     * <p>{@code requireDeactivatable}과 나눈 이유는 "본인 계정" 규칙이 비활성에만
     * 걸리기 때문이다 — 자기 이름·소속을 고치는 것은 막을 이유가 없다.
     */
    private Person requireEditable(long personId) {
        Person target = personRepository.findByIdAndActiveTrue(personId)
                .orElseThrow(NotFoundException::new);

        if (target.isSystem()) {
            throw new UnprocessableException(ErrorCode.IMMUTABLE_ACCOUNT,
                    "시스템 계정은 변경할 수 없습니다");
        }

        return target;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("이름은 필수입니다", "name");
        }

        return name.trim();
    }

    private void requireDeactivatable(long callerPersonId, Person target) {
        if (target.isSystem()) {
            throw new UnprocessableException(ErrorCode.IMMUTABLE_ACCOUNT,
                    "시스템 계정은 변경할 수 없습니다");
        }

        if (target.getId() == callerPersonId) {
            throw new UnprocessableException(ErrorCode.IMMUTABLE_ACCOUNT,
                    "본인 계정은 비활성할 수 없습니다");
        }
    }
}
