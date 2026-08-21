package kr.proten.pms;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DB가 필요한 테스트의 공통 기반 — 실물 PostgreSQL 컨테이너 하나를 공유한다.
 *
 * H2는 쓰지 않는다: 스키마를 Flyway가 소유하고(2026-08-21 결정) 마이그레이션이
 * PostgreSQL 문법이라 H2에서 의미가 달라진다(conventions §8 "방언 타는 검증은 PG").
 */
@SpringBootTest
abstract class PostgresTestBase {
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        // 싱글턴 컨테이너로 직접 기동한다 — @Container(+@Testcontainers)는 컨테이너를
        // 테스트 클래스 단위로 내리므로, 스프링 컨텍스트를 재사용하는 두 번째 클래스가
        // 죽은 커넥션 풀을 물게 된다. 종료는 Testcontainers(Ryuk)가 JVM 종료 시 처리한다.
        POSTGRES.start();
    }
}
