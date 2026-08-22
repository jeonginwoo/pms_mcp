package kr.proten.pms.notification.service.impl;

import kr.proten.pms.common.exception.NotImplementedException;
import kr.proten.pms.notification.repository.NotificationRepository;
import kr.proten.pms.notification.NotificationService;
import kr.proten.pms.notification.NotificationType;
import kr.proten.pms.notification.NotificationView;
import kr.proten.pms.notification.NotifyCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 유스케이스 — **골격만 서 있고 로직은 아직 없다** (2026-08-22).
 *
 * 이미 정해져 있는 것:
 * - 조회는 수신자로 먼저 좁힌다 — 남의 알림은 404가 아니라 애초에 질의에 없다
 * - 적재는 `dedupeKey` 선검사 + 유니크 제약 두 겹으로 멱등이다 (F1-2)
 * - 회수는 조건부 삭제 한 문장이다 (F3-3) — 읽음 처리와 겹치면 먼저 커밋한 읽음이 이긴다
 *
 * TODO(F1-1): 수신자를 정하려면 "이 인원과 같은 소속의 팀장 그룹 사용자"가 필요하다.
 *   person의 가시성 계약은 방향이 반대(화자→보이는 사람)라 그대로 쓸 수 없다.
 * TODO(F1-5): 수신자별 알림 설정(notifPrefs)은 auth의 `User`에 있고 아직 API가 없다
 *   (US-H1 H1-4). 설정 조회 경로가 생기기 전까지 필터는 항상 통과로 둔다.
 */
@Service
@Transactional
class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;

    NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationView> listMine(
            long callerPersonId, Boolean read, Pageable pageable) {
        throw new NotImplementedException("알림 목록 (F1-3)");
    }

    @Override
    public void markRead(long callerPersonId, long notificationId) {
        throw new NotImplementedException("알림 읽음 처리 (F1-3)");
    }

    @Override
    public void notify(NotifyCommand command) {
        throw new NotImplementedException("알림 적재 (F1-1·F1-2·F1-5)");
    }

    @Override
    public int withdrawUnread(String refType, long refId, NotificationType type) {
        throw new NotImplementedException("알림 회수 (F3-3)");
    }
}
