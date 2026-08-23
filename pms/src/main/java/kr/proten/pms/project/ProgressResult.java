package kr.proten.pms.project;

/**
 * 진척률 변경 결과 — MCP {@code UpdateProgressResult}가 채워지는 모양이다.
 *
 * <p>{@code executed=false}는 실패가 아니라 <b>1단계(요약)</b>다: 확인 카드를 그릴
 * 재료를 주고 DB는 그대로다(AC A2-1). {@code version}은 2단계 커밋에 그대로 쓴다.
 *
 * <p>{@code completable}은 "지금 완료 처리할 수 있는가"다 — 100% 저장이 상태를 바꾸지
 * 않는 대신(§5 자동 전이 폐지) 챗·화면이 완료(US-A7)를 유도하는 재료로 쓴다(A2-3).
 */
public record ProgressResult(
        boolean executed,
        long projectId,
        String projectName,
        int previousProgress,
        int requestedProgress,
        long version,
        boolean completable) {
}
