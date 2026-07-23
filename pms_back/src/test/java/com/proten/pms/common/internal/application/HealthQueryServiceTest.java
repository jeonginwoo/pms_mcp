package com.proten.pms.common.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 헬스 판정 유스케이스 단위 테스트 — DB 왕복 성공/실패가 UP/DOWN으로 매핑되는지 검증.
 */
@ExtendWith(MockitoExtension.class)
class HealthQueryServiceTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private HealthQueryService healthQueryService;

    @Test
    @DisplayName("DB 왕복(SELECT 1)이 성공하면 UP·database=true를 반환한다")
    void check_dbReachable_returnsUp() {
        // Given
        given(jdbcTemplate.queryForObject("select 1", Integer.class)).willReturn(1);

        // When
        HealthStatus status = healthQueryService.check();

        // Then
        assertThat(status.status()).isEqualTo("UP");
        assertThat(status.database()).isTrue();
    }

    @Test
    @DisplayName("DB 왕복이 실패하면 DOWN·database=false를 반환한다")
    void check_dbUnreachable_returnsDown() {
        // Given
        given(jdbcTemplate.queryForObject("select 1", Integer.class))
                .willThrow(new DataAccessResourceFailureException("connection refused"));

        // When
        HealthStatus status = healthQueryService.check();

        // Then
        assertThat(status.status()).isEqualTo("DOWN");
        assertThat(status.database()).isFalse();
    }
}
