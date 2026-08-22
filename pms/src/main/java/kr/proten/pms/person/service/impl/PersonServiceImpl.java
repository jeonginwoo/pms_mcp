package kr.proten.pms.person.service.impl;

import java.util.List;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.NotImplementedException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.AccountPort;
import kr.proten.pms.person.OrgVisibility;
import kr.proten.pms.person.OrgVisibilityService;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.PersonService;
import kr.proten.pms.person.service.dto.CreatePersonCommand;
import kr.proten.pms.person.service.dto.MeView;
import kr.proten.pms.person.service.dto.UpdatePersonCommand;
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
            PersonAuditRecorder personAuditRecorder) {
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
    }

    /** 가시성 범위 내 인원 목록 — 시스템 계정·비활성 인원은 제외한다. */
    @Transactional(readOnly = true)
    public List<PersonRef> listVisible(long callerPersonId) {
        OrgVisibility visibility = orgVisibilityService.visibilityOf(callerPersonId);

        if (visibility.unrestricted()) {
            return personRefFactory.toRefs(
                    personRepository.findByActiveTrueAndSystemFalseOrderByIdAsc());
        }

        return personRefFactory.toRefs(
                personRepository.findByIdInAndActiveTrueAndSystemFalseOrderByIdAsc(
                        visibility.visiblePersonIds()));
    }

    /**
     * 인원 단건 조회.
     * 노출 대상이 아닌 인원(부재·시스템 계정·비활성)과 가시성 밖 인원은 같은
     * 404다 — 사유가 응답으로 새면 존재 자체가 드러난다.
     */
    @Transactional(readOnly = true)
    public PersonRef getPerson(long callerPersonId, long personId) {
        Person target = personRepository.findByIdAndActiveTrue(personId)
                .filter(person -> !person.isSystem())
                .orElseThrow(NotFoundException::new);

        if (!orgVisibilityService.visibilityOf(callerPersonId).canView(personId)) {
            throw new NotFoundException();
        }

        return personRefFactory.toRef(target);
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

    /**
     * 인원 + 로그인 계정을 한 트랜잭션에서 만든다 (AC E2-1).
     * 둘을 쪼개지 않는 이유: 계정 없는 인원은 로그인할 수 없고, 인원 없는 계정은
     * 화자가 될 수 없다 — 절반만 만들어진 상태가 의미를 갖지 않는다.
     *
     * 계정 생성은 `AccountPort`(auth 구현)에 맡긴다 — 초기 비밀번호·해시 방식은
     * person이 알 일이 아니고, 같은 트랜잭션에 참여하므로 원자성은 그대로다.
     */
    public PersonRef create(long callerPersonId, CreatePersonCommand command) {
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

        return personRefFactory.toRef(saved);
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
     * TODO(E2-2): 바뀐 필드만 담는 스냅샷이 필요하다 — project 쪽 `ProjectAuditRecorder`와
     *   같은 역할을 `PersonAuditRecorder`가 해야 하는데 지금은 생성·비활성만 안다.
     */
    public PersonRef update(long callerPersonId, UpdatePersonCommand command) {
        orgManagePermission.require(callerPersonId);

        throw new NotImplementedException("인력 수정 (E2-2)");
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
     * TODO(E1-2): "허용하되 경고"의 경고를 어디에 실을지 — 응답 본문에 담을지
     *   알림(EPIC F)으로 보낼지 미정. AC 문구가 경로를 지정하지 않는다.
     */
    public PersonRef moveOrgUnit(long callerPersonId, long personId, long orgUnitId) {
        orgManagePermission.require(callerPersonId);

        throw new NotImplementedException("소속 조직 이동 (E1-1)");
    }

    public void deactivate(long callerPersonId, long personId) {
        orgManagePermission.require(callerPersonId);

        Person target = personRepository.findByIdAndActiveTrue(personId)
                .orElseThrow(NotFoundException::new);
        requireDeactivatable(callerPersonId, target);

        target.deactivate();
        personAuditRecorder.personDeactivated(callerPersonId, personRepository.saveAndFlush(target));
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
