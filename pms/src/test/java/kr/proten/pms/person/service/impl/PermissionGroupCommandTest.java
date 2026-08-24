package kr.proten.pms.person.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.dto.PermissionGroupCommand;
import kr.proten.pms.person.service.dto.PermissionGroupDetail;
import kr.proten.pms.person.service.entity.PermissionGroup;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 권한 그룹 관리 쓰기 (AC E5-1~E5-4).
 *
 * <p>가장 중요한 케이스는 <b>자기 잠금 방지</b>다(E5-3): 관리자 그룹이 편집·삭제 가능해지면
 * 마지막 관리자가 스스로 관리 권한을 잃고 되돌릴 입구가 §7에 없다.
 */
@ExtendWith(MockitoExtension.class)
class PermissionGroupCommandTest {
    private static final long ADMIN_ID = 1L;
    private static final long GROUP_ID = 5L;
    private static final long FIXED_GROUP_ID = 1L;

    @Mock
    private PermissionGroupRepository permissionGroupRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private OrgPermissionService orgPermissionService;
    @Mock
    private PersonAuditRecorder auditRecorder;

    private PermissionGroupServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(orgPermissionService.has(ADMIN_ID, OrgPermission.MANAGE_ORG))
                .thenReturn(true);
        service = new PermissionGroupServiceImpl(permissionGroupRepository, personRepository,
                new OrgManagePermission(orgPermissionService), auditRecorder);
    }

    @Test
    @DisplayName("E5-1 — 새 그룹은 시퀀스 id를 받고 절대 systemFixed가 아니다")
    void createNeverProducesFixedGroups() {
        // 만들 수 있게 하면 지울 수 없는 그룹을 사용자가 계속 찍어낼 수 있다
        when(permissionGroupRepository.nextId()).thenReturn(9L);
        when(permissionGroupRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        PermissionGroupDetail created = service.create(ADMIN_ID,
                command(null, "계약담당", "TEAM", false, true, false, false));

        assertThat(created.id()).isEqualTo(9L);
        assertThat(created.systemFixed()).isFalse();
        assertThat(created.visibilityScope()).isEqualTo("TEAM");
        assertThat(created.manageContracts()).isTrue();
        verify(auditRecorder).permissionGroupCreated(eq(ADMIN_ID), any());
    }

    @Test
    @DisplayName("E5-3 — systemFixed 그룹은 수정도 삭제도 422다 (자기 잠금 방지)")
    void fixedGroupIsImmutable() {
        PermissionGroup admin = PermissionGroup.of(FIXED_GROUP_ID, "관리자",
                VisibilityScope.COMPANY, true, true, true, true, true);
        when(permissionGroupRepository.findById(FIXED_GROUP_ID)).thenReturn(Optional.of(admin));

        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.update(ADMIN_ID,
                        command(FIXED_GROUP_ID, "관리자", "TEAM", false, false, false, false)))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.IMMUTABLE_GROUP));

        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.delete(ADMIN_ID, FIXED_GROUP_ID));

        verify(permissionGroupRepository, never()).delete(any());
    }

    @Test
    @DisplayName("E5-3 — 인원이 0인 고정 그룹도 여전히 못 지운다 (판정 순서)")
    void fixedGroupCheckComesBeforeInUseCheck() {
        // 고정 판정이 인원 검사보다 먼저다 — 순서가 뒤집히면 인원을 다 옮긴 관리자 그룹이
        // 지워질 수 있다. 인원 조회에 닿지도 않는 것으로 순서를 고정한다
        when(permissionGroupRepository.findById(FIXED_GROUP_ID)).thenReturn(Optional.of(
                PermissionGroup.of(FIXED_GROUP_ID, "관리자", VisibilityScope.COMPANY,
                        true, true, true, true, true)));

        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.delete(ADMIN_ID, FIXED_GROUP_ID));

        verify(personRepository, never()).existsByGroupId(any());
    }

    @Test
    @DisplayName("E5-4 — 소속 인원이 있으면 409 IN_USE")
    void deleteIsRejectedWhileMembersRemain() {
        when(permissionGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(personRepository.existsByGroupId(GROUP_ID)).thenReturn(true);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.delete(ADMIN_ID, GROUP_ID))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.IN_USE));

        verify(permissionGroupRepository, never()).delete(any());
    }

    @Test
    @DisplayName("E5-2 — 수정은 스냅샷을 먼저 뜨고 새 정의를 남긴다")
    void updateRecordsDiff() {
        PermissionGroup target = group();
        when(permissionGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(target));

        PermissionGroupDetail updated = service.update(ADMIN_ID,
                command(GROUP_ID, "팀장", "DIVISION", true, true, false, false));

        assertThat(updated.visibilityScope()).isEqualTo("DIVISION");
        assertThat(updated.createProject()).isTrue();
        verify(auditRecorder).snapshot(target);
        verify(auditRecorder).permissionGroupChanged(eq(ADMIN_ID), any(), any());
    }

    @Test
    @DisplayName("모르는 가시성 범위는 400이 아니라 422다 — 형식이 아니라 참조의 문제다")
    void unknownScopeIsUnprocessable() {
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.create(ADMIN_ID,
                        command(null, "새그룹", "WORLDWIDE", false, false, false, false)))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.REF_NOT_FOUND));
    }

    private static PermissionGroup group() {
        return PermissionGroup.of(GROUP_ID, "팀장", VisibilityScope.TEAM,
                true, true, false, false, false);
    }

    private static PermissionGroupCommand command(
            Long groupId,
            String name,
            String scope,
            boolean createProject,
            boolean manageContracts,
            boolean manageAllProjects,
            boolean manageOrg) {
        return new PermissionGroupCommand(groupId, name, scope,
                createProject, manageContracts, manageAllProjects, manageOrg, 0);
    }
}
