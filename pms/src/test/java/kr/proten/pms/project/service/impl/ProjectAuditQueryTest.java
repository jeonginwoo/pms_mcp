package kr.proten.pms.project.service.impl;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import kr.proten.pms.audit.AuditQueryService;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.common.exception.NotFoundException;
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
 * 프로젝트별 이력 조회의 가시성 판정 — AC G2-2·G2-3.
 *
 * 통합 로그(403)와 달리 여기는 404다: 볼 수 없는 프로젝트의 이력을 물으면 "권한이
 * 없다"가 아니라 "없다"로 답해야 그 프로젝트의 존재가 드러나지 않는다. 관문이 상세
 * 조회와 같은 `requireVisible`인 것이 그 일치의 근거다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectAuditQueryTest {
    private static final long CALLER_ID = 28L;
    private static final long PROJECT_ID = 7L;
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Mock
    private ProjectVisibilityService projectVisibilityService;
    @Mock
    private AuditQueryService auditQueryService;
    @Mock
    private ProjectViewFactory projectViewFactory;

    private ProjectQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProjectQueryServiceImpl(
                null, projectVisibilityService, projectViewFactory, auditQueryService);
    }

    @Test
    @DisplayName("G2-2 — 가시성 안이면 역할과 무관하게 이력 조회로 위임한다")
    void listAudit_whenVisible_delegates() {
        // Given
        Page<AuditRecord> empty = Page.empty(FIRST_PAGE);
        when(auditQueryService.findByProject(PROJECT_ID, FIRST_PAGE)).thenReturn(empty);

        // When
        service.listAudit(CALLER_ID, PROJECT_ID, FIRST_PAGE);

        // Then
        verify(projectVisibilityService).requireVisible(CALLER_ID, PROJECT_ID);
        verify(auditQueryService).findByProject(PROJECT_ID, FIRST_PAGE);
    }

    @Test
    @DisplayName("G2-3 — 가시성 밖은 403이 아니라 404이고 이력은 읽지 않는다")
    void listAudit_outsideVisibility_isNotFound() {
        // Given
        when(projectVisibilityService.requireVisible(CALLER_ID, PROJECT_ID))
                .thenThrow(new NotFoundException());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.listAudit(CALLER_ID, PROJECT_ID, FIRST_PAGE));
        verifyNoInteractions(auditQueryService);
    }
}
