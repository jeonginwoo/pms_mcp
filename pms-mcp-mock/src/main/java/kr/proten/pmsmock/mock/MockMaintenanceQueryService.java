package kr.proten.pmsmock.mock;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.port.MaintenanceLogDto;
import kr.proten.pmsmock.port.MaintenanceQueryPort;
import org.springframework.stereotype.Component;

/**
 * 유지보수 이력 인메모리 구현 — PMS 구현 후 폐기한다.
 */
@Component
public class MockMaintenanceQueryService implements MaintenanceQueryPort {
    // 허용 이력 유형(FR-AI-14)
    private static final Set<String> TYPES = Set.of("SR", "장애", "정기점검", "패치");
    // 토큰 가드 — 응답 최대 건수
    private static final int MAX_LOGS = 50;

    @Override
    public List<MaintenanceLogDto> findLogs(long id, String type) {
        if (type != null && !TYPES.contains(type)) {
            throw new IllegalArgumentException("type은 SR, 장애, 정기점검, 패치 중 하나여야 합니다: " + type);
        }

        return MockData.MAINTENANCE_LOGS.stream()
                .filter(log -> log.projectId() == id)
                .filter(log -> type == null || log.type().equals(type))
                .sorted(Comparator.comparing(MockData.MockMaintenanceLog::date).reversed())
                .limit(MAX_LOGS)
                .map(log -> new MaintenanceLogDto(log.date(), log.type(), log.content()))
                .toList();
    }
}
