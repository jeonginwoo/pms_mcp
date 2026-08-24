package kr.proten.pms.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 인앱 알림 유스케이스 — EPIC F.
 *
 * 조회는 언제나 화자 본인 것이다 — "남의 알림 목록"이라는 질문 자체가 없으므로
 * 대상 지정 파라미터를 두지 않는다.
 *
 * **적재 경로의 정본은 이벤트다**(§8) — `notify`는 <b>notification의 리스너가 자기 자신을
 * 부르는</b> 데 쓴다. 다른 모듈이 알림을 직접 만들라고 부르기 시작하면 "발행 측이
 * 구독자를 모른다"는 성질이 깨지고, 무엇보다 <b>순환이 된다</b>: resource는 이미
 * `OverbookingDetected`의 발행자라 notification이 그 타입을 import하는데(구독자 → 발행자),
 * resource가 `notify`를 부르면 반대 간선이 함께 생긴다.
 *
 * <p>2026-08-24 정정: 구 주석은 "알림을 유발하는 모듈(project·resource)이 `notify`를
 * 부르려면 이 넷이 모듈 루트에 있어야 한다"고 적고 있었는데, 바로 다음 문단과 서로
 * 반대되는 말이었다(PRD-pms §3 표도 같은 오류였다 — 함께 정정). 밖에서 쓰는 곳이
 * 0건이므로 루트 배치의 근거도 사라졌다 — 이 넷을 `service/`로 내릴지는 D-b(이슈 등록
 * 알림)가 붙을 때 함께 판단한다(미해결 등재).
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

    /** 내 알림 설정 (AC H1-4) — 유형 전체를 담는다(끈 것만 false). */
    NotificationPreferences myPreferences(long callerPersonId);

    /**
     * 내 알림 설정을 바꾼다 (AC H1-4).
     *
     * <p>부분 갱신이 아니라 <b>전체 교체</b>다: 화면이 토글 묶음을 통째로 보내고,
     * 보내지 않은 유형을 "그대로 둔다"로 해석하면 유형이 늘었을 때 새 유형의 상태가
     * 화면과 서버에서 갈린다(PUT 의미론 — §7).
     *
     * @param enabled 유형별 on/off. 빠진 유형은 <b>켜짐</b>으로 본다(opt-out 기본값)
     */
    NotificationPreferences updatePreferences(
            long callerPersonId, java.util.Map<NotificationType, Boolean> enabled);
}
