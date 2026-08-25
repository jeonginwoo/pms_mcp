package kr.proten.pms.notification.controller.stream;

import kr.proten.pms.notification.service.dto.NotificationStored;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 적재된 알림을 열린 SSE 연결로 밀어낸다 (AC F1-4).
 *
 * <p><b>이 한 줄짜리 클래스가 계층 규칙을 지키는 자리다</b>: 이미터는 웹 타입이라
 * {@code controller}에만 있을 수 있고({@code LayerRuleTest}) 적재는 {@code service}에서
 * 일어나는데, 서비스가 컨트롤러를 부르면 방향이 거꾸로다. 그래서 서비스는 이벤트만
 * 던지고 여기서 받는다 — 두 계층이 서로를 모른 채 이어진다.
 *
 * <p><b>{@code AFTER_COMMIT}</b>: 롤백된 알림을 밀어내면 화면에는 있는데 목록에는 없는
 * 알림이 생긴다. 커밋 뒤에만 나가야 정본(표)과 어긋나지 않는다.
 *
 * <p><b>동기다</b>({@code @ApplicationModuleListener}처럼 비동기가 아니다): 모듈 내부
 * 이벤트라 그 무게가 필요 없고, 푸시는 메모리의 이미터에 쓰는 일이라 짧다. 실패해도
 * {@link NotificationStream}이 삼키므로 커밋된 트랜잭션에 영향을 주지 않는다.
 */
@Component
class NotificationPusher {
    private final NotificationStream stream;

    NotificationPusher(NotificationStream stream) {
        this.stream = stream;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onStored(NotificationStored event) {
        stream.push(event.recipientId(), event.view());
    }
}
