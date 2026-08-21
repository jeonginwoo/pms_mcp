package kr.proten.pms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 컨텍스트 기동 스모크 — 빈 배선과 Flyway 마이그레이션 적용을 함께 확인한다.
 * 데이터소스는 공통 기반의 실물 PostgreSQL이다(PostgresTestBase).
 */
class PmsApplicationTest extends PostgresTestBase {
    @Test
    @DisplayName("스프링 컨텍스트가 뜨고 마이그레이션이 적용된다")
    void contextLoads() {
    }
}
