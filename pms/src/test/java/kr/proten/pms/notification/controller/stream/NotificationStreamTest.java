package kr.proten.pms.notification.controller.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import kr.proten.pms.notification.NotificationType;
import kr.proten.pms.notification.NotificationView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 이미터 레지스트리 (AC F1-4).
 *
 * <p>여기서 잠그는 것은 <b>연결이 사라져도 알림은 사라지지 않는다</b>는 성질이다.
 * F1-4가 "미연결이면 재연결·재조회 시 반영"이라고 적었으므로, 푸시 실패가 예외로
 * 올라가면 <b>보내지 못했다는 이유로 적재가 롤백</b>될 수 있다 — 그것이 이 클래스에서
 * 가장 중요한 단정이다.
 */
class NotificationStreamTest {
    private static final long PERSON_ID = 13L;

    private NotificationStream stream;

    @BeforeEach
    void setUp() {
        stream = new NotificationStream();
    }

    @Test
    @DisplayName("F1-4 — 연 뒤에는 그 사람의 열린 연결로 푸시된다")
    void pushesToAnOpenConnection() throws IOException {
        // Given
        RecordingEmitter emitter = openRecording();

        // When
        stream.push(PERSON_ID, view(1L, "배정되었습니다"));

        // Then — 이벤트 id가 알림 id다(재연결 시 Last-Event-ID가 그 값을 되보낸다)
        assertThat(emitter.sent).hasSize(1);
        assertThat(stream.openCount(PERSON_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("F1-4 — 연결이 없으면 조용히 끝난다 (알림은 표에 남아 있다)")
    void pushWithoutConnectionIsSilent() {
        // When · Then — 예외가 나면 적재 트랜잭션이 말려든다
        stream.push(PERSON_ID, view(1L, "배정되었습니다"));
        assertThat(stream.openCount(PERSON_ID)).isZero();
    }

    @Test
    @DisplayName("F1-4 — 푸시가 실패해도 예외를 올리지 않고 그 연결만 걷어낸다")
    void failedPushIsSwallowedAndTheConnectionIsDropped() {
        // Given — 이미 끊긴 연결
        FailingEmitter broken = new FailingEmitter();
        register(broken);

        // When · Then — 던지면 "보내지 못했다"는 이유로 알림이 사라진다
        stream.push(PERSON_ID, view(1L, "배정되었습니다"));
        assertThat(stream.openCount(PERSON_ID)).isZero();
    }

    @Test
    @DisplayName("F1-4 — 한 사람이 탭을 둘 열면 둘 다 받는다")
    void everyConnectionOfThatPersonReceives() throws IOException {
        // Given
        RecordingEmitter first = openRecording();
        RecordingEmitter second = openRecording();

        // When
        stream.push(PERSON_ID, view(1L, "배정되었습니다"));

        // Then
        assertThat(first.sent).hasSize(1);
        assertThat(second.sent).hasSize(1);
    }

    @Test
    @DisplayName("F1-4 — 남의 알림은 내 연결로 가지 않는다")
    void pushIsScopedToTheRecipient() throws IOException {
        // Given
        RecordingEmitter mine = openRecording();

        // When
        stream.push(99L, view(1L, "남의 알림"));

        // Then
        assertThat(mine.sent).isEmpty();
    }

    /**
     * 등록하고 <b>연결 확인 바이트를 지운다</b> — {@code register}는 첫 바이트로
     * 주석 한 줄을 흘려 보낸다(프록시·브라우저가 그것을 받아야 연결이 성립한
     * 것으로 본다). 그것까지 세면 단정이 "알림 몇 건"을 말하지 못한다.
     */
    private RecordingEmitter openRecording() throws IOException {
        RecordingEmitter emitter = new RecordingEmitter();
        register(emitter);
        emitter.sent.clear();

        return emitter;
    }

    /**
     * 전송을 볼 수 있는 사본을 등록한다 — 검증 대상은 레지스트리의 동작이고
     * 이미터 구현이 아니다. {@code register}는 {@code open}이 쓰는 같은 자리다.
     */
    private void register(SseEmitter emitter) {
        stream.register(PERSON_ID, emitter);
    }

    private static NotificationView view(long id, String message) {
        return new NotificationView(id, NotificationType.ASSIGNED, "Project", 7L, message,
                false, Instant.parse("2026-08-25T00:00:00Z"));
    }

    /** 보낸 것을 기록만 하는 이미터. */
    private static final class RecordingEmitter extends SseEmitter {
        private final List<Object> sent = new ArrayList<>();

        @Override
        public void send(SseEmitter.SseEventBuilder builder) {
            sent.add(builder);
        }
    }

    /** 언제나 끊겨 있는 이미터 — 실패 경로를 만든다. */
    private static final class FailingEmitter extends SseEmitter {
        @Override
        public void send(SseEmitter.SseEventBuilder builder) throws IOException {
            throw new IOException("broken pipe");
        }
    }
}
