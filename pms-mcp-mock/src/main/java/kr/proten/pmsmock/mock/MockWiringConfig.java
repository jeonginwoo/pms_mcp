package kr.proten.pmsmock.mock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.port.MaintenanceQueryService;
import kr.proten.pmsmock.port.PersonQueryService;
import kr.proten.pmsmock.port.ProgressCommandService;
import kr.proten.pmsmock.port.ProjectQueryService;
import kr.proten.pmsmock.port.UtilizationQueryService;

/**
 * mock 구현 배선 — mock/ 소속(폐기 대상). 실전 승격 시 이 클래스는 버려지고
 * PMS 애플리케이션 서비스 빈이 port를 구현한다 (부록 B-3 — mcp/에는 폐기물이 남지 않게 여기 둔다).
 */
@Configuration
public class MockWiringConfig {

    @Bean
    MockData mockData() {
        return new MockData();
    }

    @Bean
    VisibilityPolicy visibilityPolicy(MockData data) {
        return new VisibilityPolicy(data);
    }

    @Bean
    ProjectQueryService projectQueryService(MockData data, VisibilityPolicy visibility) {
        return new InMemoryProjectQueryService(data, visibility);
    }

    @Bean
    PersonQueryService personQueryService(MockData data, VisibilityPolicy visibility) {
        return new InMemoryPersonQueryService(data, visibility);
    }

    @Bean
    UtilizationQueryService utilizationQueryService(MockData data, VisibilityPolicy visibility) {
        return new InMemoryUtilizationQueryService(data, visibility);
    }

    @Bean
    MaintenanceQueryService maintenanceQueryService(MockData data) {
        return new InMemoryMaintenanceQueryService(data);
    }

    @Bean
    ProgressCommandService progressCommandService(MockData data, VisibilityPolicy visibility) {
        return new InMemoryProgressCommandService(data, visibility);
    }
}
