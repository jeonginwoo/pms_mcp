package kr.proten.pms.notification;


/**
 * 알림 적재 요청 (AC F1-1).
 *
 * @param dedupeKey 멱등 키 — 같은 사건에 대해 알림이 두 번 생기지 않게 한다
 *                  (F1-2 중복 이벤트 · F2-2·F3-2 스케줄러 재실행). 수신자마다
 *                  하나씩이므로 recipientId와 함께 유일해야 한다
 */
public record NotifyCommand(
        long recipientId,
        NotificationType type,
        String refType,
        Long refId,
        String message,
        String dedupeKey) {
}
