package kr.proten.pms.notification.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.notification.NotificationPreferences;
import kr.proten.pms.notification.NotificationService;
import kr.proten.pms.notification.NotificationType;
import kr.proten.pms.notification.NotificationView;
import kr.proten.pms.notification.service.dto.NotificationStored;
import kr.proten.pms.notification.NotifyCommand;
import kr.proten.pms.notification.repository.NotificationMuteRepository;
import kr.proten.pms.notification.repository.NotificationRepository;
import kr.proten.pms.notification.service.entity.Notification;
import kr.proten.pms.notification.service.entity.NotificationMute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
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
 * <p><b>F1-5(수신자 설정 필터)</b>는 적재 직전 한 줄이다 — 꺼 둔 유형이면 저장하지
 * 않는다. 설정은 notification이 소유한다({@code notification_mutes}, V12):
 * {@code V2__users.sql}이 "notifPrefs는 알림 모듈이 생길 때 추가한다"고 미뤄 둔 것을
 * 여기서 이행했고, auth의 {@code User}에 붙이지 않은 이유는 필터를 거는 쪽이
 * notification이라 거기서 auth를 읽으면 모듈 경계가 하나 더 넓어지기 때문이다.
 */
@Service
@Transactional
class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationMuteRepository muteRepository;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    NotificationServiceImpl(
            NotificationRepository notificationRepository,
            NotificationMuteRepository muteRepository,
            Clock clock,
            ApplicationEventPublisher events) {
        this.notificationRepository = notificationRepository;
        this.muteRepository = muteRepository;
        this.clock = clock;
        this.events = events;
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
        // 껐으면 적재도 하지 않는다 (F1-5) — 목록에서 숨기는 것이 아니라 만들지 않는다.
        // 나중에 켜도 그 사이 알림은 없다: "끈 동안은 오지 않는다"가 설정의 뜻이다
        if (muteRepository.existsByPersonIdAndType(command.recipientId(), command.type())) {
            return;
        }

        // 선검사 — 정상 흐름에서 유니크 제약 위반을 예외로 받지 않게 한다.
        // 동시 발행으로 이 검사를 지나쳐도 제약이 막는다(두 겹 · F1-2)
        if (notificationRepository.existsByRecipientIdAndDedupeKey(
                command.recipientId(), command.dedupeKey())) {
            return;
        }

        Notification saved = notificationRepository.save(Notification.of(
                command.recipientId(),
                command.type(),
                command.refType(),
                command.refId(),
                command.message(),
                command.dedupeKey(),
                Instant.now(clock)));
        // 커밋 후에 컨트롤러가 밀어낸다 — 롤백된 알림을 화면에 띄우지 않는다
        events.publishEvent(new NotificationStored(command.recipientId(), toView(saved)));
    }

    @Override
    public int withdrawUnread(String refType, long refId, NotificationType type) {
        return notificationRepository.deleteUnreadFor(refType, refId, type);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferences myPreferences(long callerPersonId) {
        return NotificationPreferences.of(mutedTypesOf(callerPersonId));
    }

    @Override
    public NotificationPreferences updatePreferences(
            long callerPersonId, Map<NotificationType, Boolean> enabled) {
        // 전체 교체 — 지우고 다시 넣는다. 44명 × 5유형 규모라 diff를 계산할 이유가 없고,
        // 부분 갱신으로 두면 "보내지 않은 유형"의 뜻이 애매해진다(§7 PUT 의미론)
        muteRepository.deleteByPersonId(callerPersonId);

        List<NotificationMute> muted = Arrays.stream(NotificationType.values())
                .filter(type -> Boolean.FALSE.equals(enabled.get(type)))
                .map(type -> NotificationMute.of(callerPersonId, type))
                .toList();
        muteRepository.saveAll(muted);

        return NotificationPreferences.of(muted.stream()
                .map(NotificationMute::getType)
                .collect(Collectors.toUnmodifiableSet()));
    }

    private Set<NotificationType> mutedTypesOf(long personId) {
        return muteRepository.findByPersonId(personId).stream()
                .map(NotificationMute::getType)
                .collect(Collectors.toUnmodifiableSet());
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
