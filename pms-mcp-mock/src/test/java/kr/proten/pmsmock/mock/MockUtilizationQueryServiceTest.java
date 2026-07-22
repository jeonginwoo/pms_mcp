package kr.proten.pmsmock.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.YearMonth;
import java.util.List;
import kr.proten.pmsmock.port.OverbookingDto;
import kr.proten.pmsmock.port.UtilizationEntryDto;
import kr.proten.pmsmock.port.UtilizationQueryPort;
import kr.proten.pmsmock.port.UtilizationReportDto;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 가동률 조회 목업 구현 단위 테스트. 보정 공식: rate=round(mm×100), adjustedRate=round(rate×직급계수).
 */
class MockUtilizationQueryServiceTest {
    // 순수 단위 테스트 — 스프링 컨텍스트 없이 직접 생성
    private final UtilizationQueryPort port = new MockUtilizationQueryService();

    @Test
    @DisplayName("PERSON 범위는 보정 가동률을 계산해 1건을 반환한다 (공식 앵커: 0.7MM × 계수 1.5 = 105%)")
    void report_PERSON_보정계산() {
        UtilizationReportDto report = port.report(YearMonth.of(2026, 7), "PERSON", 16L);

        assertThat(report.month()).isEqualTo("2026-07");
        assertThat(report.entries())
                .extracting(UtilizationEntryDto::name, UtilizationEntryDto::mm,
                        UtilizationEntryDto::rate, UtilizationEntryDto::adjustedRate)
                .containsExactly(Tuple.tuple("남도린", 0.7, 70, 105));
    }

    @Test
    @DisplayName("MY_TEAM 범위는 기본 요청자(남도린)의 팀원만 포함한다")
    void report_MY_TEAM_기본요청자팀만() {
        UtilizationReportDto report = port.report(YearMonth.of(2026, 7), "MY_TEAM", null);

        assertThat(report.entries())
                .extracting(UtilizationEntryDto::name)
                .containsExactlyInAnyOrder("남도린", "남민준", "전세아");
    }

    @Test
    @DisplayName("DIVISION 범위는 본부 전체를 포함한다")
    void report_DIVISION_본부전체() {
        UtilizationReportDto report = port.report(YearMonth.of(2026, 7), "DIVISION", null);

        assertThat(report.entries()).hasSize(8);
    }

    @Test
    @DisplayName("배정이 없는 월은 전원 0%로 반환한다 — 여유 인력 탐색(S-2)에 쓰인다")
    void report_배정없는월_전원0() {
        UtilizationReportDto report = port.report(YearMonth.of(2026, 12), "MY_TEAM", null);

        assertThat(report.entries()).hasSize(3);
        assertThat(report.entries())
                .allSatisfy(e -> assertThat(e.adjustedRate()).isZero());
    }

    @Test
    @DisplayName("PERSON 범위에 personId가 없으면 파라미터 오류를 던진다")
    void report_PERSON_personId누락_예외() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> port.report(YearMonth.of(2026, 7), "PERSON", null));
    }

    @Test
    @DisplayName("잘못된 scope는 파라미터 오류를 던진다")
    void report_잘못된scope_예외() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> port.report(YearMonth.of(2026, 7), "EVERYONE", null));
    }

    @Test
    @DisplayName("2026-07 과부하는 정확히 2명, 보정 가동률 내림차순이다")
    void findOverbooked_2026_07_정확히2명_내림차순() {
        List<OverbookingDto> result = port.findOverbooked(YearMonth.of(2026, 7));

        assertThat(result)
                .extracting(OverbookingDto::name, OverbookingDto::adjustedRate)
                .containsExactly(Tuple.tuple("남민준", 120), Tuple.tuple("남도린", 105));
    }

    @Test
    @DisplayName("과부하 원인 배정에 프로젝트명과 MM이 담긴다")
    void findOverbooked_원인배정_프로젝트명과MM일치() {
        List<OverbookingDto> result = port.findOverbooked(YearMonth.of(2026, 7));

        assertThat(result.getFirst().causes())
                .extracting(c -> c.projectName(), c -> c.mm())
                .containsExactlyInAnyOrder(
                        Tuple.tuple("차세대 API 게이트웨이 구축", 0.7),
                        Tuple.tuple("한국수출입은행 규정관리 재구축", 0.5));
    }

    @Test
    @DisplayName("배정이 없는 월(2026-12)과 여유로운 월(2026-08)은 과부하가 없다")
    void findOverbooked_과부하없는월_빈목록() {
        assertThat(port.findOverbooked(YearMonth.of(2026, 12))).isEmpty();
        assertThat(port.findOverbooked(YearMonth.of(2026, 8))).isEmpty();
    }
}
