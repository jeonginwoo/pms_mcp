package kr.proten.pms.common.exception;

/**
 * 골격만 서 있고 로직이 아직 없는 유스케이스 (2026-08-22 골격 확장 결정).
 *
 * 500으로 터뜨리지 않는 이유: 호출자가 "서버가 고장났다"와 "이 기능은 아직 없다"를
 * 구분할 수 있어야 하고, 프런트·`/mcp` 어댑터가 라우트 유무를 미리 확인할 수 있어야
 * 한다. §7 에러 표에 없는 코드인 것은 의도다 — **구현이 들어오면 이 예외를 던지는
 * 자리가 사라지므로 표에 오를 일이 없다.** 던지는 곳이 0이 되면 이 클래스도 지운다.
 */
public class NotImplementedException extends ApiException {

    /**
     * @param what 무엇이 아직 없는지 — AC 번호를 함께 적는다 (예: "가동률 조회 (C1-1)")
     */
    public NotImplementedException(String what) {
        super(ErrorCode.NOT_IMPLEMENTED, "아직 구현되지 않았습니다: " + what);
    }
}
