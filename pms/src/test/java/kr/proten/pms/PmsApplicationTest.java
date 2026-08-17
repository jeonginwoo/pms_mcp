package kr.proten.pms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 컨텍스트 기동 스모크 — 데이터소스는 테스트 프로필의 H2(인메모리).
 * 통합 테스트는 Testcontainers(PostgreSQL)로 별도 작성한다(conventions §8) —
 * 이 테스트는 빈 배선 확인만 담당한다.
 */
@SpringBootTest
class PmsApplicationTest {
    @Test
    @DisplayName("스프링 컨텍스트가 뜬다")
    void contextLoads() {
    }
}
