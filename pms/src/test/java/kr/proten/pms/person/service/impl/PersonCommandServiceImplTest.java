package kr.proten.pms.person.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
import kr.proten.pms.person.service.dto.CreatePersonCommand;
import kr.proten.pms.person.service.dto.OrgPermission;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 인력 등록·비활성 유스케이스 단위 테스트 — AC E2-1·E2-3~E2-5.
 * 판정자는 "사용자/조직/권한 관리" 플래그이고, 시스템 계정·본인은 고정 대상이다.
 * 등록은 인원과 로그인 계정을 한 트랜잭션에서 만든다(둘 중 하나만 있는 상태는 무의미).
 */
@ExtendWith(MockitoExtension.class)
class PersonCommandServiceImplTest {
    private static final long ADMIN_ID = 1L;
    private static final long TARGET_ID = 103L;
    private static final long SYSTEM_ID = 44L;

    @Mock
    private PersonRepository personRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrgUnitRepository orgUnitRepository;
    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private PermissionGroupRepository permissionGroupRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private PersonRefFactory personRefFactory;
    @Mock
    private OrgPermissionService orgPermissionService;
    @Mock
    private PersonAuditRecorder personAuditRecorder;

    private PersonCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonCommandServiceImpl(
                personRepository,
                userRepository,
                orgUnitRepository,
                gradeRepository,
                permissionGroupRepository,
                orgPermissionService,
                passwordHasher,
                personRefFactory,
                personAuditRecorder);
    }

    @Test
    @DisplayName("E2-1 — 인원과 로그인 계정을 함께 만든다 (초기 비밀번호는 해시로만)")
    void create_byManager_savesPersonAndAccount() {
        // Given
        givenManageOrg(true);
        givenValidReferences();
        when(userRepository.existsByEmail("new@proten.co.kr")).thenReturn(false);
        when(personRepository.nextId()).thenReturn(45L);
        when(userRepository.nextId()).thenReturn(45L);
        when(passwordHasher.hash("proten1!")).thenReturn("$2a$10$hash");
        when(personRepository.save(any(Person.class))).thenAnswer(call -> call.getArgument(0));

        // When
        service.create(ADMIN_ID, new CreatePersonCommand(
                " 신입 ", PersonFixtures.SI_TEAM_ID, 1L, 4L, "new@proten.co.kr"));

        // Then
        ArgumentCaptor<Person> person = ArgumentCaptor.forClass(Person.class);
        verify(personRepository).save(person.capture());
        assertThat(person.getValue().getId()).isEqualTo(45L);
        assertThat(person.getValue().getName()).isEqualTo("신입");
        assertThat(person.getValue().isActive()).isTrue();
        assertThat(person.getValue().isSystem()).isFalse();

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertThat(user.getValue().getEmail()).isEqualTo("new@proten.co.kr");
        assertThat(user.getValue().getPasswordHash()).isEqualTo("$2a$10$hash");
        verify(personAuditRecorder).personCreated(eq(ADMIN_ID), any(Person.class));
    }

    @Test
    @DisplayName("E2-4 — 관리 플래그가 없으면 등록도 403")
    void create_withoutManageOrg_isForbidden() {
        // Given
        givenManageOrg(false);

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.create(ADMIN_ID, validCommand()));
        verifyNoInteractions(personRepository);
    }

    @Test
    @DisplayName("E2-1 — 없는 조직·직급·그룹은 422 REF_NOT_FOUND")
    void create_unknownReference_isUnprocessable() {
        // Given
        givenManageOrg(true);
        when(orgUnitRepository.existsById(PersonFixtures.SI_TEAM_ID)).thenReturn(false);

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.create(ADMIN_ID, validCommand()))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo("REF_NOT_FOUND"));
        verify(personRepository, never()).save(any());
    }

    @Test
    @DisplayName("E2-1 — 이미 쓰는 이메일은 409 DUPLICATE_EMAIL (로그인 ID다)")
    void create_duplicateEmail_isConflict() {
        // Given
        givenManageOrg(true);
        givenValidReferences();
        when(userRepository.existsByEmail("new@proten.co.kr")).thenReturn(true);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.create(ADMIN_ID, validCommand()))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo("DUPLICATE_EMAIL"));
        verify(personRepository, never()).save(any());
    }

    @Test
    @DisplayName("E2-1 — 이름·이메일이 비면 400")
    void create_blankInput_isValidationError() {
        // Given
        givenManageOrg(true);

        // When · Then
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.create(ADMIN_ID, new CreatePersonCommand(
                        " ", PersonFixtures.SI_TEAM_ID, 1L, 4L, "new@proten.co.kr")));
    }

    @Test
    @DisplayName("E2-3 — 관리 권한자는 인원을 비활성한다 (행은 남고 이력이 생긴다)")
    void deactivate_byManager_marksInactiveAndRecordsAudit() {
        // Given
        givenManageOrg(true);
        Person target = givenActivePerson(TARGET_ID, "에스아이팀원");
        when(personRepository.saveAndFlush(target)).thenReturn(target);

        // When
        service.deactivate(ADMIN_ID, TARGET_ID);

        // Then
        assertThat(target.isActive()).isFalse();
        verify(personAuditRecorder).personDeactivated(ADMIN_ID, target);
    }

    @Test
    @DisplayName("E2-4 — 관리 플래그가 없으면 403, 대상 조회조차 하지 않는다")
    void deactivate_withoutManageOrg_isForbidden() {
        // Given
        givenManageOrg(false);

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.deactivate(ADMIN_ID, TARGET_ID));
        verifyNoInteractions(personRepository);
    }

    @Test
    @DisplayName("없는·이미 비활성인 인원은 404")
    void deactivate_unknownPerson_isNotFound() {
        // Given
        givenManageOrg(true);
        when(personRepository.findByIdAndActiveTrue(TARGET_ID)).thenReturn(Optional.empty());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.deactivate(ADMIN_ID, TARGET_ID));
    }

    @Test
    @DisplayName("E2-5 — 시스템 계정은 422 IMMUTABLE_ACCOUNT, 아무것도 안 바뀐다")
    void deactivate_systemAccount_isUnprocessable() {
        // Given
        givenManageOrg(true);
        Person system = PersonFixtures.systemAccount(SYSTEM_ID, PersonFixtures.COMPANY_ID, 1L);
        when(personRepository.findByIdAndActiveTrue(SYSTEM_ID)).thenReturn(Optional.of(system));

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.deactivate(ADMIN_ID, SYSTEM_ID))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo("IMMUTABLE_ACCOUNT"));
        assertThat(system.isActive()).isTrue();
        verify(personRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("본인 계정은 비활성할 수 없다 — 자기 잠금 방지 (422)")
    void deactivate_self_isUnprocessable() {
        // Given
        givenManageOrg(true);
        givenActivePerson(ADMIN_ID, "관리자");

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.deactivate(ADMIN_ID, ADMIN_ID))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo("IMMUTABLE_ACCOUNT"));
        verify(personRepository, never()).saveAndFlush(any());
    }

    private CreatePersonCommand validCommand() {
        return new CreatePersonCommand(
                "신입", PersonFixtures.SI_TEAM_ID, 1L, 4L, "new@proten.co.kr");
    }

    private void givenValidReferences() {
        when(orgUnitRepository.existsById(PersonFixtures.SI_TEAM_ID)).thenReturn(true);
        when(gradeRepository.existsById(1L)).thenReturn(true);
        when(permissionGroupRepository.existsById(4L)).thenReturn(true);
    }

    private void givenManageOrg(boolean granted) {
        when(orgPermissionService.has(ADMIN_ID, OrgPermission.MANAGE_ORG)).thenReturn(granted);
    }

    private Person givenActivePerson(long personId, String name) {
        Person person = PersonFixtures.person(personId, name, PersonFixtures.SI_TEAM_ID, 4L);
        when(personRepository.findByIdAndActiveTrue(personId)).thenReturn(Optional.of(person));

        return person;
    }
}
