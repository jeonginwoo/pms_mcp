package kr.proten.pms.person.service.impl;

import kr.proten.pms.person.service.impl.requester.RequesterResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.PermissionGroup;
import kr.proten.pms.person.service.entity.PersonFixtures;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 프로젝트 밖 기능 플래그 판정 서비스 단위 테스트 (상위 PRD §4-3).
 * 이 판정이 project 모듈의 A1-5(생성 권한)·A2-7(전 프로젝트 관리 치환)의 입력이다.
 */
@ExtendWith(MockitoExtension.class)
class OrgPermissionServiceImplTest {
    @Mock
    private PersonRepository personRepository;
    @Mock
    private PermissionGroupRepository permissionGroupRepository;

    private OrgPermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgPermissionServiceImpl(
                new RequesterResolver(personRepository, permissionGroupRepository));
    }

    @Test
    @DisplayName("팀장 그룹 — 프로젝트 생성은 되고 전 프로젝트 관리는 안 된다")
    void has_teamLeadGroup_grantsCreateProjectOnly() {
        // Given
        givenCallerGroup(102L, PersonFixtures.group(
                3L, "팀장", VisibilityScope.TEAM, OrgPermission.CREATE_PROJECT));

        // When · Then
        assertThat(service.has(102L, OrgPermission.CREATE_PROJECT)).isTrue();
        assertThat(service.has(102L, OrgPermission.MANAGE_ALL_PROJECTS)).isFalse();
    }

    @Test
    @DisplayName("관리자 그룹 — 플래그 4종 전부 보유(§4-1 PM 치환의 근거 포함)")
    void has_adminGroup_grantsEveryFlag() {
        // Given
        givenCallerGroup(1L, PersonFixtures.group(
                1L, "관리자", VisibilityScope.COMPANY,
                OrgPermission.CREATE_PROJECT,
                OrgPermission.MANAGE_CONTRACTS,
                OrgPermission.MANAGE_ALL_PROJECTS,
                OrgPermission.MANAGE_ORG));

        // When · Then — 열거형 4종 전부가 컬럼에 대응됨을 고정한다
        assertThat(OrgPermission.values()).allSatisfy(permission ->
                assertThat(service.has(1L, permission)).isTrue());
    }

    @Test
    @DisplayName("팀원 그룹 — 플래그 전무(A1-5의 403 근거)")
    void has_memberGroup_deniesEveryFlag() {
        // Given
        givenCallerGroup(105L, PersonFixtures.group(4L, "팀원", VisibilityScope.SELF));

        // When · Then
        assertThat(OrgPermission.values()).allSatisfy(permission ->
                assertThat(service.has(105L, permission)).isFalse());
    }

    private void givenCallerGroup(
            long personId,
            PermissionGroup group) {
        when(personRepository.findByIdAndActiveTrue(personId)).thenReturn(Optional.of(
                PersonFixtures.person(personId, "화자", PersonFixtures.SI_TEAM_ID, group.getId())));
        when(permissionGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
    }
}
