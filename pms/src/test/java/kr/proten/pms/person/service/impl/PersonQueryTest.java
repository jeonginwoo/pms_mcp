package kr.proten.pms.person.service.impl;

import kr.proten.pms.person.service.impl.requester.RequesterResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.AccountPort;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.OrgVisibility;
import kr.proten.pms.person.OrgVisibilityService;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.PersonFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 인력 조회 유스케이스 단위 테스트 — 가시성 필터와 404 은닉 (구조 원칙 3).
 * 목록은 가시성 내 부분집합만, 단건은 부재·시스템 계정·비활성·가시성 밖을 전부
 * 같은 404로 수렴시킨다 (상위 PRD §4-4).
 */
@ExtendWith(MockitoExtension.class)
class PersonQueryTest {
    @Mock
    private PersonRepository personRepository;
    @Mock
    private OrgUnitRepository orgUnitRepository;
    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private OrgVisibilityService orgVisibilityService;
    @Mock
    private PermissionGroupRepository permissionGroupRepository;
    @Mock
    private AccountPort accountPort;
    @Mock
    private OrgPermissionService orgPermissionService;
    @Mock
    private RequesterResolver requesterResolver;
    @Mock
    private PersonAuditRecorder personAuditRecorder;
    @Mock
    private kr.proten.pms.person.AssignmentCountPort assignmentCountPort;

    private PersonServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonServiceImpl(
                personRepository,
                orgUnitRepository,
                gradeRepository,
                permissionGroupRepository,
                accountPort,
                orgVisibilityService,
                new OrgManagePermission(orgPermissionService),
                requesterResolver,
                new PersonRefFactory(orgUnitRepository, gradeRepository),
                personAuditRecorder,
                assignmentCountPort);
    }

    @Test
    @DisplayName("목록 — 제한 scope는 가시 인원만 질의한다(전체 로드 금지)")
    void listVisible_restrictedScope_queriesVisibleIdsOnly() {
        // Given
        when(orgVisibilityService.visibilityOf(102L))
                .thenReturn(OrgVisibility.of(102L, Set.of(103L)));
        when(personRepository.findByIdInAndActiveTrueAndSystemFalseOrderByIdAsc(
                Set.of(102L, 103L)))
                .thenReturn(List.of(
                        PersonFixtures.person(102L, "팀장", PersonFixtures.SI_TEAM_ID, 3L),
                        PersonFixtures.person(103L, "팀원", PersonFixtures.SI_TEAM_ID, 4L)));
        givenNameTables();

        // When
        List<PersonRef> people = service.listVisible(102L);

        // Then
        assertThat(people).map(PersonRef::name).containsExactly("팀장", "팀원");
        assertThat(people.getFirst().orgUnit()).isEqualTo("SI팀");
        assertThat(people.getFirst().grade()).isEqualTo("수석");
    }

    @Test
    @DisplayName("목록 — 전사 scope는 활성·비시스템 전원을 질의한다")
    void listVisible_unrestricted_queriesEveryone() {
        // Given
        when(orgVisibilityService.visibilityOf(1L)).thenReturn(OrgVisibility.unrestricted(1L));
        when(personRepository.findByActiveTrueAndSystemFalseOrderByIdAsc())
                .thenReturn(List.of(
                        PersonFixtures.person(106L, "타부문원", PersonFixtures.OTHER_DIVISION_ID, 4L)));
        givenNameTables();

        // When
        List<PersonRef> people = service.listVisible(1L);

        // Then
        assertThat(people).map(PersonRef::name).containsExactly("타부문원");
    }

    @Test
    @DisplayName("단건 — 가시성 안이면 조회된다")
    void getPerson_visible_returnsRef() {
        // Given
        Person target = PersonFixtures.person(103L, "팀원", PersonFixtures.SI_TEAM_ID, 4L);
        when(personRepository.findByIdAndActiveTrue(103L)).thenReturn(Optional.of(target));
        when(orgVisibilityService.visibilityOf(102L))
                .thenReturn(OrgVisibility.of(102L, Set.of(103L)));
        givenNameTables();

        // When
        PersonRef found = service.getPerson(102L, 103L);

        // Then
        assertThat(found.id()).isEqualTo(103L);
        assertThat(found.orgUnit()).isEqualTo("SI팀");
    }

    @Test
    @DisplayName("단건 — 가시성 밖은 404 은닉(403이 아니다)")
    void getPerson_outsideVisibility_throwsNotFound() {
        // Given
        Person outsider =
                PersonFixtures.person(106L, "타부문원", PersonFixtures.OTHER_DIVISION_ID, 4L);
        when(personRepository.findByIdAndActiveTrue(106L)).thenReturn(Optional.of(outsider));
        when(orgVisibilityService.visibilityOf(102L))
                .thenReturn(OrgVisibility.of(102L, Set.of(103L)));

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getPerson(102L, 106L));
    }

    @Test
    @DisplayName("단건 — 부재·시스템 계정·비활성은 가시성 밖과 같은 404")
    void getPerson_absentOrHidden_throwsSameNotFound() {
        // Given
        when(personRepository.findByIdAndActiveTrue(900L)).thenReturn(Optional.empty());
        when(personRepository.findByIdAndActiveTrue(901L)).thenReturn(Optional.of(
                PersonFixtures.systemAccount(901L, PersonFixtures.COMPANY_ID, 1L)));

        // When · Then — 두 경로가 같은 예외로 수렴해야 은닉이 성립한다
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getPerson(102L, 900L));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getPerson(102L, 901L));
    }

    private void givenNameTables() {
        when(orgUnitRepository.findAll()).thenReturn(PersonFixtures.orgUnits());
        when(gradeRepository.findAllById(anyIterable()))
                .thenReturn(List.of(PersonFixtures.grade(1L, "수석", 1.5)));
    }
}
