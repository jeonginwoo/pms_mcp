package kr.proten.pms.audit;

/**
 * 감사 행위 (PRD-pms §4).
 *
 * STATE_CHANGE는 §5 프로젝트 상태 전이 전용이다 — 역할 변경·팀 이동처럼 상태가
 * 아닌 변경은 UPDATE다(PRD-pms v2.1 정리 · A6-1·E1-1). 소프트 삭제는 행이 남더라도
 * 의도가 삭제이므로 DELETE다(A4-1·B2-1).
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    STATE_CHANGE
}
