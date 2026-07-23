package com.proten.pms.common.internal.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * FE-BE-DB 연결 확인 유스케이스.
 * DB 왕복(SELECT 1) 성공 여부로 헬스 상태를 판정한다.
 * 커넥션 획득 실패까지 DOWN으로 매핑해야 하므로 트랜잭션을 걸지 않는다
 * (@Transactional이면 커넥션 획득 예외가 서비스 진입 전에 터져 여기서 잡을 수 없다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthQueryService {
    // DB 왕복 확인용 JDBC 접근자
    private final JdbcTemplate jdbcTemplate;

    /**
     * DB 왕복이 성공하면 UP, 실패하면 DOWN 상태를 반환합니다.
     */
    public HealthStatus check() {
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
        } catch (DataAccessException e) {
            log.warn("헬스체크 DB 왕복 실패: {}", e.getClass().getSimpleName());

            return new HealthStatus("DOWN", false);
        }

        return new HealthStatus("UP", true);
    }
}
