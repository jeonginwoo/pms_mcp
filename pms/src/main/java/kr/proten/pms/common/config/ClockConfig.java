package kr.proten.pms.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * 현재 시각의 단일 출처 (2026-08-23 신설).
 *
 * <p>{@code LocalDate.now()}를 코드 안에서 직접 부르면 그 지점은 테스트에서 시간을
 * 고정할 수 없다 — 배정 종료가 종료월 말일을 계산하는 순간(AC B2-1)부터 이것이
 * 실제 문제가 됐다. 시간을 주입 가능한 협력자로 두면 "8월에 종료하면 8/31"을
 * 달력과 무관하게 검증할 수 있다(conventions §4 — Boot가 관리하는 빈은 주입한다).
 *
 * <p>테스트가 {@code Clock.fixed(...)}를 자기 빈으로 올리면 그쪽이 이긴다.
 */
@Configuration
class ClockConfig {
    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
