package kr.proten.pms.notification.service.dto;

import kr.proten.pms.notification.NotificationView;

/**
 * 알림이 적재됐다 — notification <b>모듈 안에서만</b> 도는 이벤트 (AC F1-4).
 *
 * <p><b>왜 이벤트인가</b>: SSE 이미터는 웹 타입({@code SseEmitter})이라 계층 규칙상
 * {@code controller}에만 있을 수 있는데({@code LayerRuleTest} — 웹 관심사는 controller와
 * common에만), 적재는 {@code service}에서 일어난다. 서비스가 컨트롤러를 직접 부르면
 * 계층 방향이 거꾸로 간다(service → controller 금지). 그래서 서비스는 "적재됐다"만
 * 말하고, 그 말을 듣고 밀어내는 일은 컨트롤러 쪽이 한다.
 *
 * <p><b>모듈 루트가 아니라 {@code service/}에 있다</b>: 이 이벤트는 notification 밖으로
 * 나가지 않는다("모듈 루트 = 밖으로 나가는 전부" — §0). 밖의 모듈이 알림 적재를
 * 구독해야 할 일이 생기면 그때 루트로 올린다.
 *
 * <p><b>{@code @ApplicationModuleListener}가 아니라 {@code @TransactionalEventListener}로
 * 받는다</b>: 전자는 모듈 간 이벤트의 규약이고(비동기 + 커밋 후), 이것은 모듈 내부라
 * 그 무게가 필요 없다. 다만 <b>커밋 후</b>인 것은 같아야 한다 — 롤백된 알림을 밀어내면
 * 화면에 있는 알림이 목록에는 없게 된다.
 */
public record NotificationStored(long recipientId, NotificationView view) {
}
