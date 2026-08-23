package kr.proten.pms.mcp.internal;

import kr.proten.pms.mcp.internal.dto.UpdateProgressResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * FR-AI-15 — 유일한 쓰기 도구, 2단계 확인 필수 (구조 원칙 5).
 *
 * TODO(M1 project): 붙을 자리는 `ProjectLifecycleService.updateProgress`다 —
 * 2단계 확인·낙관적 락·완료 상태 규칙이 이미 그 안에 구현돼 있으므로, 계약이 project
 * 모듈 루트로 승격되면 이 도구는 위임과 표현 변환만 하게 된다.
 */
@Component
public class ProgressTools {

    @McpTool(name = "update_progress", description = """
            프로젝트 진행률을 변경한다. 반드시 2단계로 호출한다:
            먼저 confirmed=false로 호출하면 실행 없이 변경 요약만 반환된다 — 이 요약으로 사용자 확인을 받은 뒤,
            사용자가 승인한 경우에만 confirmed=true로 다시 호출한다. 확인 없이 confirmed=true로 호출해서는 안 된다.
            version은 프로젝트 상세 조회(search_projects의 projectId 지정)에서 확보한 값을 그대로 전달한다.
            version이 다르면 최신값과 함께 거절되며(다른 사용자가 먼저 수정), 자동 재시도하지 말고 최신값으로 다시 확인받아야 한다.
            취소된 확인은 재사용하지 않는다 — 사용자가 취소한 뒤 다시 요청하면 confirmed=false부터 다시 시작한다.
            100%로 저장해도 상태는 완료로 바뀌지 않는다 — 완료 처리는 화면에서만 가능하다.""")
    public UpdateProgressResult updateProgress(
            @McpToolParam(description = "프로젝트 id", required = true) int projectId,
            @McpToolParam(description = "새 진행률 (0~100)", required = true) int percent,
            @McpToolParam(description = "프로젝트 상세 조회에서 확보한 version 값", required = true) int version,
            @McpToolParam(description = "false=변경 요약만(실행 안 함), true=실제 저장 — 사용자 확인 후에만 true", required = true) boolean confirmed) {
        throw ToolError.unavailable("진행률 변경");
    }
}
