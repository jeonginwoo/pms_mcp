package kr.proten.pms.project.service.entity;

/**
 * 배정 상태 — 종료된 배정은 역할·가동률 판정 모집단에서 빠진다 (PRD-pms US-B2).
 * 종료해도 행은 남는다: 지난 달 가동률은 그때의 배정으로 계산되어야 한다.
 */
public enum AssignmentStatus {
    ACTIVE,
    CLOSED
}
