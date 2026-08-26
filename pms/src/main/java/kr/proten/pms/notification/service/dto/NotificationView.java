package kr.proten.pms.notification.service.dto;

import java.time.Instant;
import kr.proten.pms.notification.service.entity.NotificationType;

/**
 * 알림 1건 (AC F1-3).
 *
 * `refType`·`refId`를 담는 이유: 화면이 알림에서 대상으로 바로 이동해야 하는데,
 * 메시지 문자열을 파싱해 id를 꺼내는 방식은 문구가 바뀌는 순간 깨진다.
 */
public record NotificationView(
        long id,
        NotificationType type,
        String refType,
        Long refId,
        String message,
        boolean read,
        Instant createdAt) {
}
