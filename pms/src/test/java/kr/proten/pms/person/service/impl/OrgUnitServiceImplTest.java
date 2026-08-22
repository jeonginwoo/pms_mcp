package kr.proten.pms.person.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.dto.OrgUnitView;
import kr.proten.pms.person.service.entity.OrgUnit;
import kr.proten.pms.person.service.entity.PersonFixtures;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 조직 트리 관리 단위 테스트 — AC E3-3.
 * "빈 노드만 삭제 가능"이 이 서비스의 규칙이고, 목록은 그 판정 결과(deletable)를
 * 함께 실어 화면이 같은 규칙을 다시 구현하지 않게 한다.
 */
@ExtendWith(MockitoExtension.class)
class OrgUnitServiceImplTest {
    private static final long ADMIN_ID = 1L;
    private static final long EMPTY_UNIT_ID = 99L;

    @Mock
    private OrgUnitRepository orgUnitRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private OrgPermissionService orgPermissionService;
    @Mock
    private PersonAuditRecorder personAuditRecorder;

    private OrgUnitServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgUnitServiceImpl(
                orgUnitRepository,
                personRepository,
                new OrgManagePermission(orgPermissionService),
                personAuditRecorder);
    }

    @Test
    @DisplayName("목록 — 노드별 소속 인원·하위 노드 수와 삭제 가능 여부가 함께 온다")
    void list_carriesCountsAndDeletableFlag() {
        // Given
        givenManageOrg(true);
        when(orgUnitRepository.findAll()).thenReturn(List.of(
                OrgUnit.of(PersonFixtures.COMPANY_ID, null, "프로텐"),
                OrgUnit.of(PersonFixtures.SI_TEAM_ID, PersonFixtures.COMPANY_ID, "SI팀"),
                OrgUnit.of(EMPTY_UNIT_ID, PersonFixtures.COMPANY_ID, "빈팀")));
        when(personRepository.findByActiveTrue()).thenReturn(List.of(
                PersonFixtures.person(101L, "가", PersonFixtures.SI_TEAM_ID, 4L),
                PersonFixtures.person(102L, "나", PersonFixtures.SI_TEAM_ID, 4L)));

        // When
        List<OrgUnitView> views = service.list(ADMIN_ID);

        // Then
        assertThat(views).extracting(OrgUnitView::name, OrgUnitView::memberCount,
                        OrgUnitView::childCount, OrgUnitView::deletable)
                .containsExactly(
                        Tuple.tuple("프로텐", 0L, 2L, false),
                        Tuple.tuple("SI팀", 2L, 0L, false),
                        Tuple.tuple("빈팀", 0L, 0L, true));
    }

    @Test
    @DisplayName("E2-4 — 관리 플래그가 없으면 목록도 403이다 (관리 화면 전용)")
    void list_withoutManageOrg_isForbidden() {
        // Given
        givenManageOrg(false);

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.list(ADMIN_ID));
        verifyNoInteractions(orgUnitRepository);
    }

    @Test
    @DisplayName("E3-1 — 하위 노드를 만든다 (임의 깊이 허용)")
    void create_childNode_savesAndRecordsAudit() {
        // Given
        givenManageOrg(true);
        when(orgUnitRepository.existsById(PersonFixtures.SI_TEAM_ID)).thenReturn(true);
        when(orgUnitRepository.nextId()).thenReturn(18L);
        when(orgUnitRepository.save(any(OrgUnit.class))).thenAnswer(call -> call.getArgument(0));

        // When
        OrgUnitView created = service.create(ADMIN_ID, PersonFixtures.SI_TEAM_ID, " SI-2파트 ");

        // Then
        assertThat(created.id()).isEqualTo(18L);
        assertThat(created.name()).isEqualTo("SI-2파트");
        assertThat(created.parentId()).isEqualTo(PersonFixtures.SI_TEAM_ID);
        assertThat(created.deletable()).isTrue();
        verify(personAuditRecorder).orgUnitCreated(eq(ADMIN_ID), any(OrgUnit.class));
    }

    @Test
    @DisplayName("E3-1 — 없는 상위 조직은 422, 빈 이름은 400")
    void create_invalidInput_isRejected() {
        // Given
        givenManageOrg(true);
        when(orgUnitRepository.existsById(99L)).thenReturn(false);

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.create(ADMIN_ID, 99L, "새팀"))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.REF_NOT_FOUND));
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.create(ADMIN_ID, 99L, " "));
        verify(orgUnitRepository, never()).save(any());
    }

    @Test
    @DisplayName("두 번째 회사(root) 노드는 만들 수 없다 — 부문 가시성의 전제다")
    void create_secondRoot_isConflict() {
        // Given
        givenManageOrg(true);
        when(orgUnitRepository.findAll())
                .thenReturn(List.of(OrgUnit.of(PersonFixtures.COMPANY_ID, null, "프로텐")));

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.create(ADMIN_ID, null, "다른회사"))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.DUPLICATE_ROOT));
    }

    @Test
    @DisplayName("E3-3 — 빈 노드는 삭제되고 이력이 남는다")
    void delete_emptyUnit_removesAndRecordsAudit() {
        // Given
        givenManageOrg(true);
        OrgUnit target = OrgUnit.of(EMPTY_UNIT_ID, PersonFixtures.COMPANY_ID, "빈팀");
        when(orgUnitRepository.findById(EMPTY_UNIT_ID)).thenReturn(Optional.of(target));
        when(personRepository.countByOrgUnitIdAndActiveTrue(EMPTY_UNIT_ID)).thenReturn(0L);
        when(orgUnitRepository.countByParentId(EMPTY_UNIT_ID)).thenReturn(0L);

        // When
        service.delete(ADMIN_ID, EMPTY_UNIT_ID);

        // Then
        verify(orgUnitRepository).delete(target);
        verify(personAuditRecorder).orgUnitDeleted(ADMIN_ID, target);
    }

    @Test
    @DisplayName("E3-3 — 소속 인원이 있으면 409 IN_USE, 아무것도 안 지운다")
    void delete_unitWithMembers_isConflict() {
        // Given
        givenManageOrg(true);
        when(orgUnitRepository.findById(PersonFixtures.SI_TEAM_ID))
                .thenReturn(Optional.of(
                        OrgUnit.of(PersonFixtures.SI_TEAM_ID, PersonFixtures.COMPANY_ID, "SI팀")));
        when(personRepository.countByOrgUnitIdAndActiveTrue(PersonFixtures.SI_TEAM_ID))
                .thenReturn(4L);
        when(orgUnitRepository.countByParentId(PersonFixtures.SI_TEAM_ID)).thenReturn(0L);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.delete(ADMIN_ID, PersonFixtures.SI_TEAM_ID))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.IN_USE));
        verify(orgUnitRepository, never()).delete(any());
    }

    @Test
    @DisplayName("E3-3 — 하위 조직이 있으면 409 IN_USE")
    void delete_unitWithChildren_isConflict() {
        // Given
        givenManageOrg(true);
        when(orgUnitRepository.findById(PersonFixtures.COMPANY_ID))
                .thenReturn(Optional.of(OrgUnit.of(PersonFixtures.COMPANY_ID, null, "프로텐")));
        when(personRepository.countByOrgUnitIdAndActiveTrue(PersonFixtures.COMPANY_ID))
                .thenReturn(0L);
        when(orgUnitRepository.countByParentId(PersonFixtures.COMPANY_ID)).thenReturn(6L);

        // When · Then
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.delete(ADMIN_ID, PersonFixtures.COMPANY_ID));
    }

    @Test
    @DisplayName("없는 노드는 404")
    void delete_unknownUnit_isNotFound() {
        // Given
        givenManageOrg(true);
        when(orgUnitRepository.findById(EMPTY_UNIT_ID)).thenReturn(Optional.empty());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.delete(ADMIN_ID, EMPTY_UNIT_ID));
    }

    private void givenManageOrg(boolean granted) {
        when(orgPermissionService.has(ADMIN_ID, OrgPermission.MANAGE_ORG)).thenReturn(granted);
    }
}
