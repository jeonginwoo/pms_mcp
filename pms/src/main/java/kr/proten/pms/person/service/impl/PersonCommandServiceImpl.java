package kr.proten.pms.person.service.impl;

import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.repository.UserRepository;
import kr.proten.pms.person.service.OrgPermissionService;
import kr.proten.pms.person.service.PersonCommandService;
import kr.proten.pms.person.service.dto.CreatePersonCommand;
import kr.proten.pms.person.service.dto.OrgPermission;
import kr.proten.pms.person.service.dto.PersonRef;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인력 관리 유스케이스 — AC E2-1·E2-3~E2-5.
 *
 * 검사 순서는 권한(403) → 입력·참조(400·422) → 중복(409)이다: 관리 권한이 없는
 * 호출자에게 어떤 id·email이 존재하는지 알려 주지 않으려면 권한이 가장 앞이어야 한다.
 */
@Service
@Transactional
public class PersonCommandServiceImpl implements PersonCommandService {
    // 신규 계정의 초기 비밀번호 (부록 B 확정값) — 첫 로그인 후 변경 안내가 전제다
    private static final String INITIAL_PASSWORD = "proten1!";
    // 부록 B 기본값 — 월 가용 M/M 1.0 · 가동률 집계 대상
    private static final double DEFAULT_CAPACITY = 1.0;

    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final GradeRepository gradeRepository;
    private final PermissionGroupRepository permissionGroupRepository;
    private final OrgPermissionService orgPermissionService;
    private final PasswordHasher passwordHasher;
    private final PersonRefFactory personRefFactory;
    private final PersonAuditRecorder personAuditRecorder;

    public PersonCommandServiceImpl(
            PersonRepository personRepository,
            UserRepository userRepository,
            OrgUnitRepository orgUnitRepository,
            GradeRepository gradeRepository,
            PermissionGroupRepository permissionGroupRepository,
            OrgPermissionService orgPermissionService,
            PasswordHasher passwordHasher,
            PersonRefFactory personRefFactory,
            PersonAuditRecorder personAuditRecorder) {
        this.personRepository = personRepository;
        this.userRepository = userRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.gradeRepository = gradeRepository;
        this.permissionGroupRepository = permissionGroupRepository;
        this.orgPermissionService = orgPermissionService;
        this.passwordHasher = passwordHasher;
        this.personRefFactory = personRefFactory;
        this.personAuditRecorder = personAuditRecorder;
    }

    /**
     * 인원 + 로그인 계정을 한 트랜잭션에서 만든다 (AC E2-1).
     * 둘을 쪼개지 않는 이유: 계정 없는 인원은 로그인할 수 없고, 인원 없는 계정은
     * 화자가 될 수 없다 — 절반만 만들어진 상태가 의미를 갖지 않는다.
     */
    public PersonRef create(long callerPersonId, CreatePersonCommand command) {
        requireManageOrg(callerPersonId);
        requireText(command.name(), "name");
        requireText(command.email(), "email");
        requireReferences(command);
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
        userRepository.save(User.of(
                userRepository.nextId(),
                saved.getId(),
                command.email().trim(),
                passwordHasher.hash(INITIAL_PASSWORD),
                null));
        personAuditRecorder.personCreated(callerPersonId, saved);

        return personRefFactory.toRef(saved);
    }

    public void deactivate(long callerPersonId, long personId) {
        requireManageOrg(callerPersonId);

        Person target = personRepository.findByIdAndActiveTrue(personId)
                .orElseThrow(NotFoundException::new);
        requireDeactivatable(callerPersonId, target);

        target.deactivate();
        personAuditRecorder.personDeactivated(callerPersonId, personRepository.saveAndFlush(target));
    }

    private void requireManageOrg(long callerPersonId) {
        if (!orgPermissionService.has(callerPersonId, OrgPermission.MANAGE_ORG)) {
            throw new ForbiddenException("사용자·조직 관리 권한이 없습니다");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("필수 입력값입니다", field);
        }
    }

    /** 조직·직급·권한 그룹 참조 검증 (A1-3과 같은 의미론의 422). */
    private void requireReferences(CreatePersonCommand command) {
        requireExists(orgUnitRepository.existsById(command.orgUnitId()), "조직", command.orgUnitId());
        requireExists(gradeRepository.existsById(command.gradeId()), "직급", command.gradeId());
        requireExists(permissionGroupRepository.existsById(command.groupId()), "권한 그룹",
                command.groupId());
    }

    private void requireExists(boolean exists, String label, Long id) {
        if (!exists) {
            throw new UnprocessableException("REF_NOT_FOUND", "없는 %s입니다: %s".formatted(label, id));
        }
    }

    /** 로그인 ID 중복 (AC E2-1 — H1-2와 같은 코드). */
    private void requireUniqueEmail(String email) {
        if (userRepository.existsByEmail(email.trim())) {
            throw new ConflictException("DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다");
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
            throw new UnprocessableException("IMMUTABLE_ACCOUNT",
                    "시스템 계정은 변경할 수 없습니다");
        }

        if (target.getId() == callerPersonId) {
            throw new UnprocessableException("IMMUTABLE_ACCOUNT",
                    "본인 계정은 비활성할 수 없습니다");
        }
    }
}
