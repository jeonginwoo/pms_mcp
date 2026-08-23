package kr.proten.pms.audit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.proten.pms.audit.AuditAction;
import kr.proten.pms.audit.AuditEntry;
import kr.proten.pms.audit.AuditSource;
import kr.proten.pms.audit.repository.AuditLogRepository;
import kr.proten.pms.audit.service.entity.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * 감사 기록 단위 테스트 — 스냅샷이 JSON으로 굳고 입구(source)가 함께 남는지.
 * 여기서는 직렬화 표현만 본다: 매퍼는 부트가 구성한 것을 주입받으므로 실제 앱의
 * 표현(날짜 ISO 문자열 등)은 통합 테스트가 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AuditTrailImplTest {
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AuditSourceResolver auditSourceResolver;

    private AuditTrailImpl auditTrail;

    @BeforeEach
    void setUp() {
        auditTrail = new AuditTrailImpl(
                auditLogRepository, auditSourceResolver, JsonMapper.builder().build());
    }

    @Test
    @DisplayName("G1-1 — 변경 1건이 before·after JSON을 담은 한 행으로 남는다")
    void record_change_savesOneRowWithJsonSnapshots() {
        // Given
        when(auditSourceResolver.current()).thenReturn(AuditSource.WEB);
        Map<String, Object> before = state("progress", 90);
        Map<String, Object> after = state("progress", 100);

        // When
        auditTrail.record(new AuditEntry(
                "Project", 7L, 7L, AuditAction.UPDATE, 103L, before, after));

        // Then
        AuditLog saved = captureSaved();
        assertThat(saved.getEntityType()).isEqualTo("Project");
        assertThat(saved.getEntityId()).isEqualTo(7L);
        assertThat(saved.getProjectId()).isEqualTo(7L);
        assertThat(saved.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(saved.getActorId()).isEqualTo(103L);
        assertThat(saved.getSource()).isEqualTo(AuditSource.WEB);
        assertThat(saved.getBeforeState()).isEqualTo("{\"progress\":90}");
        assertThat(saved.getAfterState()).isEqualTo("{\"progress\":100}");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("생성 이력 — before가 없으면 null로 남는다 (빈 객체가 아니다)")
    void record_creation_leavesBeforeNull() {
        // Given
        when(auditSourceResolver.current()).thenReturn(AuditSource.WEB);

        // When
        auditTrail.record(new AuditEntry("Project", 7L, 7L, AuditAction.CREATE, 102L,
                null, state("name", "포털 재구축")));

        // Then
        AuditLog saved = captureSaved();
        assertThat(saved.getBeforeState()).isNull();
        assertThat(saved.getAfterState()).contains("포털 재구축");
    }

    @Test
    @DisplayName("MCP 경로 변경은 source=MCP로 남는다 — 챗 쓰기와 화면 쓰기를 갈라 본다")
    void record_mcpRequest_marksSourceMcp() {
        // Given
        when(auditSourceResolver.current()).thenReturn(AuditSource.MCP);

        // When
        auditTrail.record(new AuditEntry("Project", 7L, 7L, AuditAction.UPDATE, 103L,
                state("progress", 90), state("progress", 95)));

        // Then
        assertThat(captureSaved().getSource()).isEqualTo(AuditSource.MCP);
    }

    @Test
    @DisplayName("스냅샷은 필드 순서를 유지하고 날짜·열거도 함께 직렬화된다")
    void record_snapshot_keepsFieldOrder() {
        // Given
        when(auditSourceResolver.current()).thenReturn(AuditSource.WEB);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", AuditAction.CREATE);
        after.put("startDate", LocalDate.of(2026, 8, 1));
        after.put("solution", null);

        // When
        auditTrail.record(
                new AuditEntry("Project", 7L, 7L, AuditAction.UPDATE, 103L, null, after));

        // Then
        assertThat(captureSaved().getAfterState())
                .isEqualTo("{\"status\":\"CREATE\",\"startDate\":\"2026-08-01\","
                        + "\"solution\":null}");
    }

    private Map<String, Object> state(String field, Object value) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(field, value);

        return state;
    }

    private AuditLog captureSaved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        return captor.getValue();
    }
}
