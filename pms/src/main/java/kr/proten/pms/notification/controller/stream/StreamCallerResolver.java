package kr.proten.pms.notification.controller.stream;

/**
 * SSE 스트림의 호출자 식별 — <b>쿼리 파라미터</b>로만 (PRD-pms §7).
 *
 * <p><b>왜 별도 계약인가</b>: {@code CallerIdentityResolver}(common)를 넓혀 쿼리
 * 파라미터도 보게 하면 <b>모든 라우트</b>가 {@code ?access_token=}으로 불릴 수 있게 되고,
 * 그러면 토큰이 액세스 로그에 남는 자리가 앱 전체로 퍼진다. 쿼리 토큰은 EventSource가
 * 헤더를 싣지 못해서 어쩔 수 없이 여는 구멍이므로, <b>그 구멍은 이 라우트 하나로
 * 좁혀 둔다</b> — 그것이 이 인터페이스가 common이 아니라 notification 안에 있는 이유다.
 *
 * <p>구현이 둘이고 {@code pms.auth.enabled}가 하나를 고른다 —
 * {@code CallerIdentityResolver}와 같은 배치다. 인증이 꺼진 동안에는 파라미터가
 * personId를 그대로 나르고 그것을 신뢰한다: 헤더를 신뢰하는 것과 같은 신뢰 모델이고,
 * <b>그 상태의 앱은 외부에 노출하면 안 된다</b>는 규칙도 그대로다.
 *
 * <p><b>액세스 로그 마스킹은 앱 밖의 몫이다</b>(구현 노트 §6): Nginx 로그 포맷에서
 * {@code access_token}을 가려야 한다. 앱은 그 값을 스스로 로그에 남기지 않는 것까지만
 * 책임진다 — 그래서 이 계층의 예외 문구에도 토큰이 들어가지 않는다.
 */
interface StreamCallerResolver {

    /**
     * 쿼리 파라미터에서 호출자 personId.
     *
     * <p>식별할 수 없으면 {@code UnauthenticatedException}을 던지고, <b>컨트롤러가 그것을
     * 401로 바꾼다</b> — 전역 핸들러(§7 JSON 봉투)에 맡기면 이 요청의
     * {@code Accept: text/event-stream}과 협상이 깨진다(2026-08-25 실측).
     */
    long resolve(String accessToken);
}
