package kr.proten.pmsmock.model;

/**
 * 월별 배정 M/M. 시드에 월별 데이터가 없어 목업에서 심은 값이다
 * (적재 규칙은 PMS-M1 전 확정 — PROGRESS 미해결 이슈). month 형식 "yyyy-MM".
 */
public record Assignment(int personId, int projectId, String month, double mm) {
}
