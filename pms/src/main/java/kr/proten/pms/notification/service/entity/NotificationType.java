package kr.proten.pms.notification.service.entity;

/**
 * 알림 종류 (EPIC F) — 수신자의 알림 설정(notifPrefs — H1-4)이 켜고 끄는 단위이기도 하다.
 *
 * <p><b>자리는 `service/entity/`다</b>(2026-08-26에 모듈 루트에서 내려왔다). 구 주석은
 * `AuditAction`을 들어 "알림을 유발하는 모듈이 이 어휘를 봐야 하므로 계약 옆에 둔다"고
 * 적었는데, 그 전제가 §8 이벤트 방향 확정으로 무너졌다 — 유발하는 모듈은 <b>이벤트를
 * 발행할 뿐</b> 이 열거를 보지 않는다(밖에서 import하는 자리 0건, 실측). `AuditAction`이
 * `audit/` 루트에 남아 있는 것은 그쪽은 <b>실제로 밖에서 쓰이기 때문</b>이지, 열거라서가
 * 아니다. 그리고 `Notification`·`NotificationMute`가 이것을 `@Enumerated`로 쓰므로
 * 영속 모델의 어휘가 맞다.
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
