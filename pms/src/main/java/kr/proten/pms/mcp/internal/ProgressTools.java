package kr.proten.pms.mcp.internal;

import kr.proten.pms.mcp.internal.dto.UpdateProgressResult;
import kr.proten.pms.project.ProgressCommandService;
import kr.proten.pms.project.ProgressResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * FR-AI-15 — 유일한 쓰기 도구, 2단계 확인 필수 (구조 원칙 5). 2026-08-23 실연결.
 *
 * **2단계 확인 프로토콜을 여기서 구현하지 않는다**: `ProjectLifecycleService`가 갖고
 * 있고 이 도구는 `confirmed`를 왕복시키기만 한다. 프로토콜을 두 곳에 두면 한쪽이
 * 확인을 건너뛰는 길이 생긴다. 권한·낙관적 락·완료 규칙도 같은 이유로 도메인 몫이며,
 * 챗과 화면이 같은 서비스를 지나므로 같은 거절을 받는다.
 *
 * **감사 출처는 배선하지 않는다**: `AuditSourceResolver`가 요청 경로 접두사(`/mcp`)로
 * 판정하므로 도구가 넘길 것이 없다. 대신 그 판정이 실제로 MCP로 잡히는지는 이 도구가
 * 실연결된 뒤에야 실측할 수 있어, 관통 테스트가 그것을 함께 단정한다.
 *
 * **`summary`만 어댑터가 만든다**: 도메인 `ProgressResult`에 없는 필드다. 확인 카드에
 * 그대로 실을 한 줄이고 표현은 이 모듈 소관이다 — eval D류 채점이 "요약에 현재값 →
 * 새값 명시"를 보므로 그 두 값을 반드시 담는다.
 */
@Component
public class ProgressTools {

    private final ProgressCommandService progress;
    private final CallerContext caller;

    public ProgressTools(ProgressCommandService progress, CallerContext caller) {
        this.progress = progress;
        this.caller = caller;
    }

    @McpTool(name = "update_progress", description = """
            프로젝트 진행률을 변경한다. 반드시 2단계로 호출한다:
            먼저 confirmed=false로 호출하면 실행 없이 변경 요약만 반환된다 — 이 요약으로 사용자 확인을 받은 뒤,
            사용자가 승인한 경우에만 confirmed=true로 다시 호출한다. 확인 없이 confirmed=true로 호출해서는 안 된다.
            version은 프로젝트 상세 조회(search_projects의 projectId 지정)에서 확보한 값을 그대로 전달한다.
            version이 다르면 최신값과 함께 거절되며(다른 사용자가 먼저 수정), 자동 재시도하지 말고 최신값으로 다시 확인받아야 한다.
            취소된 확인은 재사용하지 않는다 — 사용자가 취소한 뒤 다시 요청하면 confirmed=false부터 다시 시작한다.
            100%로 저장해도 상태는 완료로 바뀌지 않는다 — 완료 처리는 화면에서만 가능하다.""",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = true,
                    idempotentHint = false, openWorldHint = false))
    public UpdateProgressResult updateProgress(
            @McpToolParam(description = "프로젝트 id", required = true) int projectId,
            @McpToolParam(description = "새 진행률 (0~100)", required = true) int percent,
            @McpToolParam(description = "프로젝트 상세 조회에서 확보한 version 값", required = true) int version,
            @McpToolParam(description = "false=변경 요약만(실행 안 함), true=실제 저장 — 사용자 확인 후에만 true", required = true) boolean confirmed) {
        long callerId = caller.callerId();
        ProgressResult result = ToolCalls.translating(() -> progress.updateProgress(
                callerId, projectId, percent, version, confirmed));

        return new UpdateProgressResult(
                result.executed(),
                (int) result.projectId(),
                result.projectName(),
                result.previousProgress(),
                result.requestedProgress(),
                (int) result.version(),
                result.completable(),
                summaryOf(result));
    }

    /**
     * 확인 카드에 실을 한 줄. 두 단계의 문장이 갈리는 이유는 커밋 뒤에는 "현재값"과
     * "요청값"이 같은 값이 되기 때문이다(`ProgressUpdateResult.committedOf`) — 그때
     * "95% → 95%"로 적으면 사용자가 무엇이 바뀌었는지 읽을 수 없다.
     */
    private static String summaryOf(ProgressResult result) {
        if (!result.executed()) {
            return "%s: 진행률 %d%% → %d%% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
                    .formatted(result.projectName(),
                            result.previousProgress(), result.requestedProgress());
        }

        String saved = "%s: 진행률 %d%%로 저장했습니다"
                .formatted(result.projectName(), result.requestedProgress());

        // 완료 처리는 도구로 노출하지 않으므로(구조 원칙 5) 경로만 알려 준다 (AC A2-3)
        return result.completable()
                ? saved + " — 완료 처리가 가능한 상태이며, 완료는 화면에서만 할 수 있습니다"
                : saved;
    }
}
