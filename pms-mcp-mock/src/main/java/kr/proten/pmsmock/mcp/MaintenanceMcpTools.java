package kr.proten.pmsmock.mcp;

import java.util.List;
import kr.proten.pmsmock.port.MaintenanceLogDto;
import kr.proten.pmsmock.port.MaintenanceQueryPort;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 유지보수 이력 MCP 도구 — 실전 PMS의 maintenance 모듈 internal/web으로 그대로 승격한다.
 * 응답은 서버에서 최근 50건으로 절단한다(FR-AI-14).
 */
@Component
public class MaintenanceMcpTools {
    // 유지보수 이력 포트 (실전에서는 application 서비스)
    private final MaintenanceQueryPort maintenanceQuery;

    public MaintenanceMcpTools(MaintenanceQueryPort maintenanceQuery) {
        this.maintenanceQuery = maintenanceQuery;
    }

    @McpTool(name = "list_maintenance_logs",
             description = "프로젝트/유지보수 계약의 SR·장애·정기점검·패치 이력을 조회한다. type으로 필터 가능")
    public List<MaintenanceLogDto> listMaintenanceLogs(
            @McpToolParam(description = "프로젝트 ID 또는 유지보수 계약 ID", required = true) Long id,
            @McpToolParam(description = "이력 유형: SR|장애|정기점검|패치. 생략 시 전체", required = false) String type) {
        return maintenanceQuery.findLogs(id, type);
    }
}
