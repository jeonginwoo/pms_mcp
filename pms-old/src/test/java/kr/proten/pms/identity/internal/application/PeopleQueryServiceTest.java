package kr.proten.pms.identity.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.common.NotFoundException;
import kr.proten.pms.identity.internal.domain.Grade;
import kr.proten.pms.identity.internal.domain.OrgUnit;
import kr.proten.pms.identity.internal.domain.PermissionGroup;
import kr.proten.pms.identity.internal.domain.Person;
import kr.proten.pms.identity.internal.domain.VisibilityScope;
import kr.proten.pms.identity.internal.domain.repository.GradeRepository;
import kr.proten.pms.identity.internal.domain.repository.OrgUnitRepository;
import kr.proten.pms.identity.internal.domain.repository.PermissionGroupRepository;
import kr.proten.pms.identity.internal.domain.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 인력 조회 유스케이스 단위 테스트 — 가시성 필터·목록 제외 규칙·404 은닉.
 * 저장소는 Mockito 목, 판정 로직(OrgTree·PersonVisibility)은 실물 사용.
 */
class PeopleQueryServiceTest {
    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final PermissionGroupRepository groupRepository = mock(PermissionGroupRepository.class);
    private final OrgUnitRepository orgUnitRepository = mock(OrgUnitRepository.class);
    private final GradeRepository gradeRepository = mock(GradeRepository.class);
    private PeopleQueryService service;

    // 그룹: 1=관리자(COMPANY) · 2=팀원(SELF)
    private final PermissionGroup companyGroup = new PermissionGroup(
            1L, "관리자", VisibilityScope.COMPANY, true, true, true, true, true, 0L);
    private final PermissionGroup selfGroup = new PermissionGroup(
            2L, "팀원", VisibilityScope.SELF, false, false, false, false, false, 0L);

    // 인원: 10=관리자 · 11=팀원 · 12=같은 팀 동료 · 13=시스템 계정 · 14=비활성
    private final Person admin = new Person(10L, "관리자", 1L, 1L, 1L, 1.0, true, false, true, 0L);
    private final Person member = new Person(11L, "팀원", 3L, 1L, 2L, 1.0, true, false, true, 0L);
    private final Person teammate = new Person(12L, "동료", 3L, 1L, 2L, 1.0, true, false, true, 0L);
    private final Person systemAccount = new Person(13L, "시스템", 1L, 1L, 1L, 0.0, false, true, true, 0L);
    private final Person inactive = new Person(14L, "퇴사자", 3L, 1L, 2L, 1.0, true, false, false, 0L);

    @BeforeEach
    void setUp() {
        service = new PeopleQueryService(
                new RequesterResolver(personRepository, groupRepository),
                personRepository,
                orgUnitRepository,
                gradeRepository);
        when(orgUnitRepository.findAll()).thenReturn(List.of(
                new OrgUnit(1L, null, "프로텐", 0L),
                new OrgUnit(2L, 1L, "솔루션사업부", 0L),
                new OrgUnit(3L, 2L, "SI팀", 0L)));
        when(gradeRepository.findAll()).thenReturn(List.of(new Grade(1L, "책임", 1.3, 0L)));
        when(personRepository.findAll()).thenReturn(
                List.of(admin, member, teammate, systemAccount, inactive));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(companyGroup));
        when(groupRepository.findById(2L)).thenReturn(Optional.of(selfGroup));
        when(personRepository.findById(10L)).thenReturn(Optional.of(admin));
        when(personRepository.findById(11L)).thenReturn(Optional.of(member));
        when(personRepository.findById(12L)).thenReturn(Optional.of(teammate));
        when(personRepository.findById(13L)).thenReturn(Optional.of(systemAccount));
        when(personRepository.findById(14L)).thenReturn(Optional.of(inactive));
        when(personRepository.findById(99L)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("목록 — SELF scope는 본인만")
    void listVisible_selfScope_returnsOnlySelf() {
        List<PeopleQueryService.PersonSummary> result = service.listVisible(11L);

        assertThat(result).extracting(PeopleQueryService.PersonSummary::id)
                .containsExactly(11L);
    }

    @Test
    @DisplayName("목록 — COMPANY여도 시스템 계정·비활성은 제외 (④·E2-3)")
    void listVisible_companyScope_excludesSystemAndInactive() {
        List<PeopleQueryService.PersonSummary> result = service.listVisible(10L);

        assertThat(result).extracting(PeopleQueryService.PersonSummary::id)
                .containsExactly(10L, 11L, 12L);
    }

    @Test
    @DisplayName("목록 — 조직명·직급명 매핑")
    void listVisible_mapsOrgUnitAndGradeNames() {
        List<PeopleQueryService.PersonSummary> result = service.listVisible(11L);

        assertThat(result.getFirst().orgUnit()).isEqualTo("SI팀");
        assertThat(result.getFirst().grade()).isEqualTo("책임");
    }

    @Test
    @DisplayName("단건 — 가시성 밖은 부재와 같은 404 (은닉 동형)")
    void getPerson_outsideVisibility_throwsSameNotFoundAsMissing() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getPerson(11L, 12L));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getPerson(11L, 99L));
    }

    @Test
    @DisplayName("단건 — 시스템 계정·비활성도 404로 은닉")
    void getPerson_systemOrInactive_throwsNotFound() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getPerson(10L, 13L));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getPerson(10L, 14L));
    }

    @Test
    @DisplayName("단건 — 가시성 내 대상은 요약 반환")
    void getPerson_visible_returnsSummary() {
        when(orgUnitRepository.findById(3L))
                .thenReturn(Optional.of(new OrgUnit(3L, 2L, "SI팀", 0L)));
        when(gradeRepository.findById(1L))
                .thenReturn(Optional.of(new Grade(1L, "책임", 1.3, 0L)));

        PeopleQueryService.PersonSummary result = service.getPerson(10L, 12L);

        assertThat(result.name()).isEqualTo("동료");
        assertThat(result.orgUnit()).isEqualTo("SI팀");
    }

    @Test
    @DisplayName("호출자가 비활성 — 토큰 문제로 취급(401)")
    void listVisible_inactiveCaller_throwsInvalidToken() {
        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> service.listVisible(14L));
    }
}
