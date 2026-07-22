package kr.proten.pmsmock.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import kr.proten.pmsmock.port.MaintenanceLogDto;
import kr.proten.pmsmock.port.MaintenanceQueryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 유지보수 이력 조회 목업 구현 단위 테스트. 106번 프로젝트에 64건이 심겨 있다.
 */
class MockMaintenanceQueryServiceTest {
    // 순수 단위 테스트 — 스프링 컨텍스트 없이 직접 생성
    private final MaintenanceQueryPort port = new MockMaintenanceQueryService();

    @Test
    @DisplayName("이력은 최신순으로 최근 50건까지만 반환한다 (FR-AI-14 토큰 가드)")
    void findLogs_50건절단_최신순() {
        List<MaintenanceLogDto> result = port.findLogs(106L, null);

        assertThat(result).hasSize(50);
        assertThat(result).isSortedAccordingTo(
                (a, b) -> b.date().compareTo(a.date()));
        assertThat(result.getFirst().date()).isEqualTo("2026-07-08");
    }

    @Test
    @DisplayName("type 필터는 절단과 함께 동작한다")
    void findLogs_type필터_장애() {
        List<MaintenanceLogDto> result = port.findLogs(106L, "장애");

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(log -> assertThat(log.type()).isEqualTo("장애"));
    }

    @Test
    @DisplayName("최신 패치 일자를 정확히 반환한다 (eval C-02 앵커)")
    void findLogs_패치_최신날짜() {
        List<MaintenanceLogDto> result = port.findLogs(101L, "패치");

        assertThat(result.getFirst().date()).isEqualTo("2026-07-10");
    }

    @Test
    @DisplayName("이력이 없는 ID는 빈 목록을 반환한다")
    void findLogs_없는Id_빈목록() {
        List<MaintenanceLogDto> result = port.findLogs(999L, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("잘못된 type은 파라미터 오류를 던진다")
    void findLogs_잘못된type_예외() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> port.findLogs(106L, "업그레이드"));
    }
}
