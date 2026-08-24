package kr.proten.pms.notification.service.impl;

import java.time.Clock;
import java.time.Instant;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.notification.NotificationService;
import kr.proten.pms.notification.NotificationType;
import kr.proten.pms.notification.NotificationView;
import kr.proten.pms.notification.NotifyCommand;
import kr.proten.pms.notification.repository.NotificationRepository;
import kr.proten.pms.notification.service.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 유스케이스 (AC F1-1·F1-2·F1-3 · F3-3 회수).
 *
 * <p>조회는 <b>언제나 수신자로 먼저 좁힌다</b> — 남의 알림은 404를 받는 것이 아니라
 * 애초에 질의에 들어오지 않는다. 읽음 처리만 단건이라 소유 검사가 필요하고, 거기서는
 * 남의 것을 <b>404로 은닉</b>한다(403이면 "그런 알림이 있다"가 새어 나간다).
 *
 * <p>적재는 {@code dedupeKey} 선검사 + 유니크 제약 <b>두 겹</b>으로 멱등이다(F1-2).
 * 선검사만 두면 동시 발행에서 뚫리고, 제약만 두면 정상 흐름이 예외로 시끄러워진다.
 *
 * <p><b>F1-5(수신자 설정 필터)는 아직 없다</b>: 설정을 저장할 자리가 없다 —
 * {@code V2__users.sql}이 "notifPrefs는 알림 모듈이 생길 때 추가한다"고 미뤄 뒀고
 * H1-4 라우트도 미착수다. 지금은 <b>필터를 항상 통과</b>시키고, 설정이 생기면 이
 * 메서드의 이른 반환 한 줄로 들어온다(미해결 등재 — PRD-pms §12).
 */
@Service
@Transactional
class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    NotificationServiceImpl(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationView> listMine(long callerPersonId, Boolean read, Pageable pageable) {
        Page<Notification> found = read == null
                ? notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                        callerPersonId, pageable)
                : notificationRepository.findByRecipientIdAndReadOrderByCreatedAtDesc(
                        callerPersonId, read, pageable);

        return found.map(NotificationServiceImpl::toView);
    }

    @Override
    public void markRead(long callerPersonId, long notificationId) {
        Notification target = notificationRepository.findById(notificationId)
                .orElseThrow(NotFoundException::new);

        // 남의 알림은 부재와 같은 답이다 — 403이면 "그런 알림이 있다"가 드러난다
        if (!target.getRecipientId().equals(callerPersonId)) {
            throw new NotFoundException();
        }

        target.markRead();
    }

    @Override
    public void notify(NotifyCommand command) {
        // 선검사 — 정상 흐름에서 유니크 제약 위반을 예외로 받지 않게 한다.
        // 동시 발행으로 이 검사를 지나쳐도 제약이 막는다(두 겹 · F1-2)
        if (notificationRepository.existsByRecipientIdAndDedupeKey(
                command.recipientId(), command.dedupeKey())) {
            return;
        }

        notificationRepository.save(Notification.of(
                command.recipientId(),
                command.type(),
                command.refType(),
                command.refId(),
                command.message(),
                command.dedupeKey(),
                Instant.now(clock)));
    }

    @Override
    public int withdrawUnread(String refType, long refId, NotificationType type) {
        return notificationRepository.deleteUnreadFor(refType, refId, type);
    }

    private static NotificationView toView(Notification notification) {
        return new NotificationView(
                notification.getId(),
                notification.getType(),
                notification.getRefType(),
                notification.getRefId(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
