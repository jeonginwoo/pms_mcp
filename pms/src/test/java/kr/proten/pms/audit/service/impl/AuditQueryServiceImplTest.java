package kr.proten.pms.audit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import kr.proten.pms.audit.AuditAction;
import kr.proten.pms.audit.AuditRecord;
import kr.proten.pms.audit.AuditSource;
import kr.proten.pms.audit.repository.AuditLogRepository;
import kr.proten.pms.audit.service.entity.AuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * 감사 조회 단위 테스트 (G1-3 · G2-2).
 *
 * 이 서비스가 지켜야 하는 것은 둘이다: 판정을 하지 않는 것(권한·가시성은 호출자가
 * 이미 세웠다 — 여기서 또 보면 모듈 순환이다)과, 정렬을 호출자에게 맡기지 않는 것.
 */
@ExtendWith(MockitoExtension.class)
class AuditQueryServiceImplTest {
    private static final long PROJECT_ID = 7L;

    @Mock
    private AuditLogRepository auditLogRepository;

    private final AuditRecordFactory factory = new AuditRecordFactory(JsonMapper.builder().build());

    private AuditQueryServiceImpl service() {
        return new AuditQueryServiceImpl(auditLogRepository, factory);
    }

    @Test
    @DisplayName("G1-3 — 호출자의 정렬은 버리고 최신순 질의로 간다")
    void findAll_dropsCallerSort() {
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of()));

        // 이력은 시간 순서가 의미의 일부다 — ?sort=actorId 하나로 뒤집히면 안 된다
        service().findAll(PageRequest.of(2, 20, Sort.by("actorId")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository)
                .findAllByOrderByCreatedAtDesc(captor.capture());

        assertThat(captor.getValue().getSort().isSorted()).isFalse();
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("G2-2 — projectId 필터 질의로 가고 판정은 하지 않는다")
    void findByProject_filtersWithoutJudging() {
        when(auditLogRepository.findByProjectIdOrderByCreatedAtDesc(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(log("{\"progress\":90}", "{\"progress\":100}"))));

        // 호출자 id를 받지 않는다는 사실이 곧 "여기서 판정하지 않는다"는 뜻이다
        AuditRecord record = service().findByProject(PROJECT_ID, PageRequest.of(0, 20))
                .getContent()
                .getFirst();

        assertThat(record.projectId()).isEqualTo(PROJECT_ID);
        assertThat(record.before()).containsEntry("progress", 90);
        assertThat(record.after()).containsEntry("progress", 100);
    }

    @Test
    @DisplayName("CREATE의 before처럼 없는 스냅샷은 null로 남는다 — 빈 맵과 구분된다")
    void nullSnapshotStaysNull() {
        when(auditLogRepository.findByProjectIdOrderByCreatedAtDesc(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(log(null, "{\"name\":\"명화공업 MES\"}"))));

        AuditRecord record = service().findByProject(PROJECT_ID, PageRequest.of(0, 20))
                .getContent()
                .getFirst();

        assertThat(record.before()).isNull();
        assertThat(record.after()).containsEntry("name", "명화공업 MES");
    }

    @Test
    @DisplayName("깨진 스냅샷 한 행이 목록 전체를 세우지 않는다 — 그 행만 값이 빈다")
    void brokenSnapshotDoesNotFailThePage() {
        // append-only라 고칠 수 없는 행이다(G1-2). 이력 화면이 열리지 않는 쪽이 더 나쁘다
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of(log("{not json", "{\"progress\":100}"))));

        AuditRecord record = service().findAll(PageRequest.of(0, 20)).getContent().getFirst();

        assertThat(record.before()).isNull();
        assertThat(record.after()).containsEntry("progress", 100);
        assertThat(record.id()).isEqualTo(41L);
    }

    private static AuditLog log(String before, String after) {
        AuditLog log = AuditLog.of(
                "Project",
                PROJECT_ID,
                PROJECT_ID,
                AuditAction.UPDATE,
                18L,
                AuditSource.WEB,
                before,
                after);
        ReflectionTestUtils.setField(log, "id", 41L);
        ReflectionTestUtils.setField(log, "createdAt", Instant.parse("2026-08-23T00:00:00Z"));

        return log;
    }
}
