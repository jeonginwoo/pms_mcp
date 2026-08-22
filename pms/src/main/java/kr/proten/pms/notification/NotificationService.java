package kr.proten.pms.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 인앱 알림 유스케이스 — EPIC F.
 *
 * 조회는 언제나 화자 본인 것이다 — "남의 알림 목록"이라는 질문 자체가 없으므로
 * 대상 지정 파라미터를 두지 않는다.
 *
 * 이 계약과 그 어휘(`NotificationType`)·값(`NotifyCommand`·`NotificationView`)이
 * 모듈 루트에 있는 이유: 알림을 유발하는 모듈(project·resource)이 `notify`를 부르려면
 * 이 넷이 전부 보여야 한다. 하위 패키지에 두면 그 호출이 모듈 경계 위반이 된다
 * (2026-08-22 — audit·person의 공개 계약과 같은 배치).
 *
 * 다만 **적재 경로의 정본은 이벤트다**(§8) — 다른 모듈이 알림을 직접 만들라고 부르기
 * 시작하면 "발행 측이 구독자를 모른다"는 성질이 깨진다. `notify`는 구독자 자신이
 * 쓰기 위한 것이다.
 */
public interface NotificationService {

    /**
     * 내 알림 목록 (AC F1-3).
     *
     * @param read null이면 전체 · false면 미읽음만 · true면 읽은 것만.
     *             boolean 하나로 받지 않는 이유: `?read=true`가 조용히 "전체"로
     *             해석되면 필터를 건 호출자가 200과 함께 틀린 답을 받는다
     */
    Page<NotificationView> listMine(
            long callerPersonId, Boolean read, Pageable pageable);

    /** 읽음 처리 (AC F1-3) — 남의 알림은 404로 은닉한다. */
    void markRead(long callerPersonId, long notificationId);

    /**
     * 알림을 적재한다 (AC F1-1).
     * 같은 `dedupeKey`가 이미 있으면 아무것도 하지 않는다 — 멱등(F1-2).
     * 수신자 설정이 그 유형을 껐으면 적재도 푸시도 하지 않는다(F1-5).
     */
    void notify(NotifyCommand command);

    /**
     * 특정 대상의 미읽음 알림을 회수한다 (AC F3-3 — 재개 시 완료 지연 알림).
     *
     * 읽은 알림은 남긴다: 이미 본 사실을 없던 일로 만들지 않는다. 회수와 읽음 처리가
     * 겹치면 **먼저 커밋한 읽음이 이긴다** — 조건부 삭제 한 문장이라 DB가 술어를 다시
     * 평가하기 때문이다(`NotificationRepository.deleteUnreadFor`).
     *
     *  실제로 회수된 건수
     */
    int withdrawUnread(String refType, long refId, NotificationType type);
}
