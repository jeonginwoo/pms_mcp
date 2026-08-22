package kr.proten.pms.audit;

/**
 * 변경이 들어온 입구 (PRD-pms §4) — 화면에서 바꿨는지 챗(MCP 도구)에서 바꿨는지.
 *
 * AI가 쓰기를 하는 시스템이라 이 구분이 감사의 최소 요건이다: `update_progress`로
 * 바뀐 진척률은 MCP로 남아야 사후에 사람의 조작과 갈라 볼 수 있다(상위 원칙 5).
 */
public enum AuditSource {
    WEB,
    MCP
}
