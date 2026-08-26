package kr.proten.pms.notification.controller.stream;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import kr.proten.pms.notification.service.dto.NotificationView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 열려 있는 SSE 연결 묶음 (AC F1-4) — 수신자별로 이미터를 들고 있다가 즉시 푸시한다.
 *
 * <p><b>이것은 저장소가 아니다.</b> 알림의 정본은 {@code notifications} 표이고 여기는
 * "지금 누가 듣고 있나"만 안다. 그래서 푸시가 실패해도 알림은 잃어버리지 않는다 —
 * F1-4가 "미연결이면 재연결·재조회 시 반영"이라고 적은 것이 그 뜻이고, 이 클래스가
 * 죽어도 목록 조회(F1-3)는 그대로 답한다.
 *
 * <p><b>한 사람이 여러 연결을 가질 수 있다</b>: 탭을 둘 열면 이미터가 둘이다. 그래서
 * 값이 목록이고, 목록은 {@code CopyOnWriteArrayList}다 — 푸시(읽기)가 연결·해제(쓰기)보다
 * 압도적으로 잦고, 순회 중 해제가 일어나도 예외가 나지 않아야 한다.
 *
 * <p><b>인스턴스가 여럿이면 이 방식은 부족하다</b>(ASSUMPTION): 이미터는 이 JVM의
 * 메모리에 있으므로 앱을 두 대로 늘리면 다른 대에 붙은 연결로는 못 보낸다. 44명 규모의
 * 단일 인스턴스 전제(PRD-pms §3)에서 맞는 선택이고, 늘리는 날 브로커(Redis pub/sub 등)가
 * 필요하다 — 그때 바뀌는 것은 이 클래스 하나다.
 */
@Component
class NotificationStream {
    /**
     * 이미터 수명 — 브라우저가 알아서 재연결하므로 서버가 영원히 붙들 이유가 없다.
     * 30분마다 한 번 끊으면 죽은 연결이 쌓이지 않는다(프록시도 대개 이보다 짧다).
     */
    private static final long TIMEOUT_MS = 30L * 60 * 1000;

    private static final Logger log = LoggerFactory.getLogger(NotificationStream.class);

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 구독을 연다 (AC F1-4).
     *
     * <p>연결 직후 주석 한 줄을 흘려 보낸다: 프록시·브라우저가 첫 바이트를 받아야
     * 연결이 성립한 것으로 보고, 그 전에는 {@code onopen}이 뜨지 않아 화면이 "연결됨"을
     * 알 수 없다.
     */
    SseEmitter open(long personId) {
        return register(personId, new SseEmitter(TIMEOUT_MS));
    }

    /**
     * 이미 만들어진 이미터를 등록한다 — {@link #open}이 부르는 자리이고,
     * 수명 훅과 첫 바이트 전송이 여기 모여 있다. 이미터를 만드는 일과 등록하는
     * 일을 가른 이유는 <b>등록의 규칙이 검증 대상</b>이기 때문이다(수명 훅 3종을
     * 다 걸었는가, 끊긴 연결이 남지 않는가).
     */
    SseEmitter register(long personId, SseEmitter emitter) {
        // compute로 원자화한다: computeIfAbsent + add를 나누면 remove가 그 사이에
        // 빈 목록을 걷어내 <b>방금 등록한 연결이 맵 밖에 남는다</b>(2026-08-25 리뷰)
        emitters.compute(personId, (key, open) -> {
            List<SseEmitter> list = open == null ? new CopyOnWriteArrayList<>() : open;
            list.add(emitter);

            return list;
        });

        emitter.onCompletion(() -> remove(personId, emitter));
        emitter.onTimeout(() -> remove(personId, emitter));
        // onError까지 걸어야 끊긴 연결이 남지 않는다 — 클라이언트가 탭을 닫으면
        // completion이 아니라 error로 오는 경우가 있다
        emitter.onError(error -> remove(personId, emitter));

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove(personId, emitter);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 그 사람의 열린 연결 전부에 한 건 보낸다 (AC F1-4).
     *
     * <p><b>실패해도 삼킨다</b>: 푸시는 편의이고 정본은 표다. 여기서 예외를 올리면
     * 알림 적재 트랜잭션이 롤백돼 <b>보내지 못했다는 이유로 알림 자체가 사라진다</b> —
     * 그것이 F1-4의 "미연결이면 재연결·재조회 시 반영"과 정반대다.
     *
     * <p>{@code id}에 알림 id를 싣는 이유는 재연결 복구다: 브라우저가 끊기면
     * {@code Last-Event-ID}로 그 값을 되보내고, 그때부터의 것만 다시 흘려 보낸다.
     */
    void push(long personId, NotificationView view) {
        List<SseEmitter> open = emitters.get(personId);

        if (open == null || open.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : open) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(view.id()))
                        .name("notification")
                        .data(view));
            } catch (IOException | IllegalStateException e) {
                // 이미 끊긴 연결이다 — 목록에서 빼고 넘어간다
                remove(personId, emitter);
                log.debug("SSE 푸시 실패 — 연결을 정리한다 (personId={})", personId);
            }
        }
    }

    /** 열린 연결 수 — 테스트와 운영 점검용. */
    int openCount(long personId) {
        return emitters.getOrDefault(personId, List.of()).size();
    }

    private void remove(long personId, SseEmitter emitter) {
        emitters.computeIfPresent(personId, (key, open) -> {
            open.remove(emitter);

            return open.isEmpty() ? null : open;
        });
    }
}
