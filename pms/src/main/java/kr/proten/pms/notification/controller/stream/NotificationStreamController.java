package kr.proten.pms.notification.controller.stream;

import kr.proten.pms.common.exception.UnauthenticatedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 알림 SSE 스트림 (AC F1-4 · PRD-pms §7 `GET /api/notifications/stream`).
 *
 * <p><b>이 라우트만 쿼리 파라미터로 인증한다</b>: EventSource는 커스텀 헤더를 싣지
 * 못해서 {@code Authorization}도 {@code X-Caller-Person-Id}도 보낼 수 없다. 그 구멍을
 * 앱 전체로 퍼뜨리지 않으려고 {@link StreamCallerResolver}를 이 패키지 안에 따로 뒀다 —
 * common의 {@code CallerIdentityResolver}를 넓혔다면 모든 라우트가 {@code ?access_token=}으로
 * 불릴 수 있게 되고, 토큰이 액세스 로그에 남는 자리가 앱 전체가 된다.
 *
 * <p><b>액세스 로그 마스킹은 배포의 몫이다</b>(구현 노트 §6 — Nginx 로그 포맷에서
 * {@code access_token}을 가려야 한다). 앱이 지는 책임은 그 값을 <b>스스로</b> 로그에
 * 남기지 않는 것까지이고, 그래서 이 계층의 예외 문구에도 토큰이 들어가지 않는다.
 * {@code frontend/README.md}와 `pms/CLAUDE.md`에 그 요구를 적어 뒀다.
 *
 * <p><b>목록 라우트와 분리한 이유</b>: 응답 타입도(`SseEmitter` vs `ApiResponse`) 인증
 * 경로도 다르다. 한 컨트롤러에 두면 "이 클래스의 인증은 무엇인가"에 답이 둘이 된다.
 */
@RestController
class NotificationStreamController {
    private final StreamCallerResolver streamCallerResolver;
    private final NotificationStream stream;

    NotificationStreamController(
            StreamCallerResolver streamCallerResolver,
            NotificationStream stream) {
        this.streamCallerResolver = streamCallerResolver;
        this.stream = stream;
    }

    /**
     * 구독을 연다 (AC F1-4).
     *
     * <p><b>서버는 끊겨 있던 동안의 것을 재생하지 않는다</b>(2026-08-25 리뷰 후 제거):
     * 화면이 연결될 때마다 목록을 다시 읽는 것이 AC F1-4의 "재연결·재조회 시 반영"
     * 그대로이고 더 단순하다. 재생을 두면 세 가지가 따라왔다 — 재생분이 그 사람의
     * <b>모든</b> 연결로 브로드캐스트돼 다른 탭의 커서를 되감고, 상한(50건)에 걸려
     * 잘린 것을 알릴 방법이 없고, 재생 중 예외가 나면 이미터가 훅 없이 누수된다.
     *
     * <p>{@code X-Accel-Buffering: no}를 싣는 이유는 프록시다: Nginx가 SSE 응답을
     * 버퍼링하면 "즉시 푸시"가 죽는다(구현 노트 §6). 앱이 헤더로 말해 두면 프록시
     * 설정을 못 만지는 환경에서도 산다.
     */
    @GetMapping(path = "/api/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> stream(
            @RequestParam(name = "access_token", required = false) String accessToken) {
        long callerPersonId;

        try {
            callerPersonId = streamCallerResolver.resolve(accessToken);
        } catch (UnauthenticatedException e) {
            // 여기서 직접 401을 낸다 — 전역 핸들러는 §7 JSON 봉투를 내는데 이 요청의
            // Accept는 text/event-stream이라 협상이 깨진다(2026-08-25 실측: 401이 아니라
            // ServletException이 그대로 터졌다). EventSource는 상태 코드만 읽고,
            // 이벤트 스트림에 실린 오류 봉투는 어차피 읽을 수단이 없다
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(stream.open(callerPersonId));
    }


}
