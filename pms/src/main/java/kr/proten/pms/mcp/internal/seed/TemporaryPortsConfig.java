package kr.proten.pms.mcp.internal.seed;

import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import kr.proten.pms.mcp.ContractSummary;
import kr.proten.pms.mcp.MaintenanceLogsResult;
import kr.proten.pms.mcp.MaintenanceQueryService;
import kr.proten.pms.mcp.OverbookedEntry;
import kr.proten.pms.mcp.PersonQueryService;
import kr.proten.pms.mcp.ProgressCommandService;
import kr.proten.pms.mcp.ProjectDetail;
import kr.proten.pms.mcp.ProjectQueryService;
import kr.proten.pms.mcp.ProjectSummary;
import kr.proten.pms.mcp.ToolError;
import kr.proten.pms.mcp.UpdateProgressResult;
import kr.proten.pms.mcp.UtilizationEntry;
import kr.proten.pms.mcp.UtilizationQueryService;

/**
 * port 임시 배선 — 전부 교체 대상 (mock/의 MockWiringConfig와 같은 격리 원리).
 * PersonQueryService만 시드 실데이터(게이트 M0: whoami가 그 사용자를 반환),
 * 나머지 4종은 도메인 서비스 구현 전까지 준비 중 응답(FR-AI-26 표준 형식).
 * 도구 카탈로그 8종은 그대로 노출된다 — 모델은 오류 문구를 읽고 실패 사실을
 * 안내한다(H-01과 같은 경로). 각 모듈 서비스가 port를 구현하면 해당 빈을 지운다.
 */
@Configuration
public class TemporaryPortsConfig {

    @Bean
    SeedPeople seedPeople(@Value("${pms.mcp.seed-people-path}") String path) {
        return new SeedPeople(Path.of(path));
    }

    @Bean
    PersonQueryService personQueryService(SeedPeople seed) {
        return new SeedPersonQueryService(seed);
    }

    @Bean
    ProjectQueryService projectQueryService() {
        return new ProjectQueryService() {
            @Override
            public List<ProjectSummary> searchProjects(int callerId, String status, String keyword) {
                throw ToolError.unavailable("프로젝트 조회");
            }

            @Override
            public ProjectDetail getProjectDetail(int callerId, int projectId) {
                throw ToolError.unavailable("프로젝트 조회");
            }
        };
    }

    @Bean
    UtilizationQueryService utilizationQueryService() {
        return new UtilizationQueryService() {
            @Override
            public List<UtilizationEntry> getUtilization(int callerId, String month, String scope,
                    Integer personId) {
                throw ToolError.unavailable("가동률 조회");
            }

            @Override
            public List<OverbookedEntry> listOverbooked(int callerId, String month) {
                throw ToolError.unavailable("가동률 조회");
            }
        };
    }

    @Bean
    MaintenanceQueryService maintenanceQueryService() {
        return new MaintenanceQueryService() {
            @Override
            public MaintenanceLogsResult listLogs(int callerId, int id, String type) {
                throw ToolError.unavailable("유지보수 조회");
            }

            @Override
            public List<ContractSummary> searchContracts(int callerId, String keyword, String status) {
                throw ToolError.unavailable("유지보수 조회");
            }
        };
    }

    @Bean
    ProgressCommandService progressCommandService() {
        return (callerId, projectId, percent, version, confirmed) -> {
            throw ToolError.unavailable("진행률 변경");
        };
    }
}
