package kr.proten.pms.person.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.person.service.dto.OrgVisibility;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.service.entity.PermissionGroup;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 조직 가시성 판정 서비스 단위 테스트 (상위 PRD §4-3·§4-4).
 * 그룹 scope → 조직 집합 → 가시 인원 id 집합으로 접히는 경로를 화자별로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class OrgVisibilityServiceImplTest {
    @Mock
    private PersonRepository personRepository;
    @Mock
    private OrgUnitRepository orgUnitRepository;
    @Mock
    private PermissionGroupRepository permissionGroupRepository;

    private OrgVisibilityServiceImpl service;

    @BeforeEach
    void setUp() {
        RequesterResolver resolver =
                new RequesterResolver(personRepository, permissionGroupRepository);
        service = new OrgVisibilityServiceImpl(
                resolver,
                personRepository,
                orgUnitRepository,
                List.of(
                        new CompanyScopeResolver(),
                        new DivisionScopeResolver(),
                        new TeamScopeResolver(),
                        new SelfScopeResolver()));
    }

    @Test
    @DisplayName("관리자(전사) — 조직 질의 없이 unrestricted")
    void visibilityOf_companyScope_isUnrestricted() {
        // Given
        givenCaller(1L, PersonFixtures.COMPANY_ID, 1L, VisibilityScope.COMPANY);

        // When
        OrgVisibility visibility = service.visibilityOf(1L);

        // Then
        assertThat(visibility.unrestricted()).isTrue();
        verify(personRepository, never()).findByOrgUnitIdInAndActiveTrue(anyCollection());
    }

    @Test
    @DisplayName("팀장(팀) — 소속 subtree 인원만 가시 집합에 든다")
    void visibilityOf_teamScope_collectsSubtreeMembers() {
        // Given
        givenCaller(102L, PersonFixtures.SI_TEAM_ID, 3L, VisibilityScope.TEAM);
        when(orgUnitRepository.findAll()).thenReturn(PersonFixtures.orgUnits());
        when(personRepository.findByOrgUnitIdInAndActiveTrue(
                List.of(PersonFixtures.SI_TEAM_ID, PersonFixtures.SI_PART_ID)))
                .thenReturn(List.of(
                        PersonFixtures.person(103L, "팀원", PersonFixtures.SI_TEAM_ID, 4L),
                        PersonFixtures.person(104L, "파트원", PersonFixtures.SI_PART_ID, 4L)));

        // When
        OrgVisibility visibility = service.visibilityOf(102L);

        // Then
        assertThat(visibility.unrestricted()).isFalse();
        assertThat(visibility.visiblePersonIds()).containsExactlyInAnyOrder(102L, 103L, 104L);
    }

    @Test
    @DisplayName("팀원(본인) — 본인만 가시, 조직 질의 없음")
    void visibilityOf_selfScope_seesSelfOnly() {
        // Given
        givenCaller(105L, PersonFixtures.CS_TEAM_ID, 4L, VisibilityScope.SELF);
        when(orgUnitRepository.findAll()).thenReturn(PersonFixtures.orgUnits());

        // When
        OrgVisibility visibility = service.visibilityOf(105L);

        // Then
        assertThat(visibility.visiblePersonIds()).containsExactly(105L);
        verify(personRepository, never()).findByOrgUnitIdInAndActiveTrue(anyCollection());
    }

    private void givenCaller(long personId, long orgUnitId, long groupId, VisibilityScope scope) {
        Person caller = PersonFixtures.person(personId, "화자", orgUnitId, groupId);
        PermissionGroup group = PersonFixtures.group(groupId, "그룹", scope);
        when(personRepository.findByIdAndActiveTrue(personId)).thenReturn(Optional.of(caller));
        when(permissionGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
    }
}
