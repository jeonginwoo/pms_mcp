package kr.proten.pms.person.service.impl;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import kr.proten.pms.audit.AuditQueryService;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 통합 감사 로그 조회의 **권한 판정** 단위 테스트 — AC G1-3.
 *
 * 조회 자체는 아직 골격이지만 403은 지금 성립해야 한다: 플래그 없는 호출자에게
 * 조직·계정 변경 이력이 새는 것은 나중에 고칠 일이 아니다. 그래서 판정만 검증하고
 * 데이터 경로는 위임 여부로 본다.
 */
@ExtendWith(MockitoExtension.class)
class AuditViewServiceImplTest {
    private static final long ADMIN_ID = 1L;
    private static final long MEMBER_ID = 28L;
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Mock
    private AuditQueryService auditQueryService;
    @Mock
    private OrgPermissionService orgPermissionService;

    private AuditViewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditViewServiceImpl(auditQueryService, orgPermissionService);
    }

    @Test
    @DisplayName("G1-3 — 관리 플래그가 있으면 조회로 위임한다")
    void listAll_byManager_delegates() {
        // Given
        Page<AuditRecord> empty = Page.empty(FIRST_PAGE);
        when(orgPermissionService.has(ADMIN_ID, OrgPermission.MANAGE_ORG)).thenReturn(true);
        when(auditQueryService.findAll(FIRST_PAGE)).thenReturn(empty);

        // When
        service.listAll(ADMIN_ID, FIRST_PAGE);

        // Then
        verify(auditQueryService).findAll(FIRST_PAGE);
    }

    @Test
    @DisplayName("G1-3 — 플래그가 없으면 403이고 조회는 아예 일어나지 않는다")
    void listAll_withoutManageOrg_isForbiddenAndNeverQueries() {
        // Given
        when(orgPermissionService.has(MEMBER_ID, OrgPermission.MANAGE_ORG)).thenReturn(false);

        // When · Then
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.listAll(MEMBER_ID, FIRST_PAGE));
        verifyNoInteractions(auditQueryService);
    }
}
