package kr.proten.pms.notification;

/**
 * 알림 종류 (EPIC F) — 수신자의 알림 설정(notifPrefs — H1-4)이 켜고 끄는 단위이기도 하다.
 *
 * `AuditAction`과 같은 이유로 `service/entity/`가 아니라 계약 옆에 둔다: 알림을
 * 유발하는 모듈이 이 어휘를 봐야 하는데, 그러자고 notification의 영속 모델을
 * 열 수는 없다.
 */
public enum NotificationType {
    /** 배정됨 — `MemberAssignedToProject` (§8) */
    ASSIGNED("프로젝트 배정"),
    /** 과부하 감지 — `OverbookingDetected` → 같은 소속 팀장 (F1-1) */
    OVERBOOKED("과부하 감지"),
    /** 완료·이관 안내 — `ProjectCompleted` (§8) */
    PROJECT_COMPLETED("프로젝트 완료"),
    /** 마감 임박 — 종료일 D-7 진행중 프로젝트의 PM (F2-1) */
    DEADLINE_NEAR("마감 임박"),
    /** 완료 지연 — 100%인 채 7일 경과한 프로젝트의 PM·PL (F3-1) */
    COMPLETION_OVERDUE("완료 지연"),
    /**
     * 이슈 담당 지정 — `MaintenanceIssueRegistered` → 그 이슈의 담당자 (D3-1).
     *
     * <p>이름이 "등록"이 아니라 "담당 지정"인 것은 <b>수신자가 담당자</b>이기 때문이다
     * (사용자 결정 2026-08-24). 등록됐다는 사실 자체는 등록한 사람의 관심사이고,
     * 알림을 받아 행동할 사람은 그 이슈를 맡게 된 사람이다.
     */
    ISSUE_ASSIGNED("이슈 담당 지정");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
