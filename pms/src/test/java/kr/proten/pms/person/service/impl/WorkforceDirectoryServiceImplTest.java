package kr.proten.pms.person.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import kr.proten.pms.person.WorkforceProfile;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.OrgUnit;
import kr.proten.pms.person.service.entity.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 가동률용 인원 속성 계약 단위 테스트 (AC C1-1·C1-5·C1-6).
 *
 * 이 계약의 핵심은 조직 트리를 <b>팀·부문 두 이름으로 펴는 것</b>이다 — 가시성
 * DIVISION scope와 같은 해석(root 직계 자식 = 부문)을 써야 "내 부문"이 화면과
 * 집계에서 갈라지지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class WorkforceDirectoryServiceImplTest {
    private static final long ROOT_ID = 1L;
    private static final long DIVISION_ID = 2L;
    private static final long TEAM_ID = 3L;
    private static final long PERSON_ID = 18L;
    private static final long GRADE_ID = 5L;

    @Mock
    private PersonRepository personRepository;
    @Mock
    private OrgUnitRepository orgUnitRepository;
    @Mock
    private GradeRepository gradeRepository;
    @InjectMocks
    private WorkforceDirectoryServiceImpl service;

    private List<OrgUnit> tree;

    @BeforeEach
    void setUp() {
        tree = List.of(
                OrgUnit.of(ROOT_ID, null, "프로텐"),
                OrgUnit.of(DIVISION_ID, ROOT_ID, "AX기술연구소"),
                OrgUnit.of(TEAM_ID, DIVISION_ID, "AX개발팀"));
    }

    @Test
    @DisplayName("C1-6 — 팀은 소속 노드, 부문은 root 직계 조상으로 갈라 싣는다")
    void findProfiles_splitsTeamAndDivision() {
        when(orgUnitRepository.findAll()).thenReturn(tree);
        when(personRepository.findAllById(anyCollection()))
                .thenReturn(List.of(person(TEAM_ID, 1.0, true)));
        when(gradeRepository.findAllById(anyCollection()))
                .thenReturn(List.of(Grade.of(GRADE_ID, "책임", 1.2)));

        WorkforceProfile profile = service.findProfiles(Set.of(PERSON_ID)).getFirst();

        assertThat(profile.team()).isEqualTo("AX개발팀");
        assertThat(profile.division()).isEqualTo("AX기술연구소");
        assertThat(profile.gradeCoeff()).isEqualTo(1.2);
    }

    @Test
    @DisplayName("부문 직속 인원은 팀과 부문이 같은 이름이다 — 그 사람에겐 그것이 사실이다")
    void findProfiles_divisionMember_teamEqualsDivision() {
        when(orgUnitRepository.findAll()).thenReturn(tree);
        when(personRepository.findAllById(anyCollection()))
                .thenReturn(List.of(person(DIVISION_ID, 1.0, true)));
        when(gradeRepository.findAllById(anyCollection())).thenReturn(List.of());

        WorkforceProfile profile = service.findProfiles(Set.of(PERSON_ID)).getFirst();

        assertThat(profile.team()).isEqualTo("AX기술연구소");
        assertThat(profile.division()).isEqualTo("AX기술연구소");
    }

    @Test
    @DisplayName("C1-5 — billable·가용 M/M을 그대로 싣고 거르지는 않는다")
    void findProfiles_carriesBillableWithoutFiltering() {
        when(orgUnitRepository.findAll()).thenReturn(tree);
        when(personRepository.findAllById(anyCollection()))
                .thenReturn(List.of(person(TEAM_ID, 0.5, false)));
        when(gradeRepository.findAllById(anyCollection())).thenReturn(List.of());

        List<WorkforceProfile> profiles = service.findProfiles(Set.of(PERSON_ID));

        // 모집단 판정은 호출자(resource)의 몫 — 여기서 빼면 개인 단건 조회가 막힌다
        assertThat(profiles).hasSize(1);
        assertThat(profiles.getFirst().billable()).isFalse();
        assertThat(profiles.getFirst().defaultCapacity()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("없는 직급은 계수 1.0 — 보정 지표 하나 때문에 조회가 실패하지 않는다")
    void findProfiles_missingGrade_fallsBackToOne() {
        when(orgUnitRepository.findAll()).thenReturn(tree);
        when(personRepository.findAllById(anyCollection()))
                .thenReturn(List.of(person(TEAM_ID, 1.0, true)));
        when(gradeRepository.findAllById(anyCollection())).thenReturn(List.of());

        assertThat(service.findProfiles(Set.of(PERSON_ID)).getFirst().gradeCoeff()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("빈 명단이면 질의하지 않는다")
    void findProfiles_emptyRoster_skipsQuery() {
        assertThat(service.findProfiles(Set.of())).isEmpty();

        verify(personRepository, never()).findAllById(anyCollection());
    }

    @Test
    @DisplayName("E3-4 — subtree 인원은 하위 조직까지 훑고 재직자만 센다")
    void findPersonIdsInSubtree_walksDescendantsAndKeepsActiveOnly() {
        when(orgUnitRepository.findAll()).thenReturn(tree);
        when(personRepository.findByOrgUnitIdInAndActiveTrue(Set.of(DIVISION_ID, TEAM_ID)))
                .thenReturn(List.of(person(TEAM_ID, 1.0, true)));

        assertThat(service.findPersonIdsInSubtree(DIVISION_ID)).containsExactly(PERSON_ID);
    }

    private Person person(long orgUnitId, double capacity, boolean billable) {
        return Person.of(
                PERSON_ID, "전세아", orgUnitId, GRADE_ID, 4L, capacity, billable, false, true);
    }
}
