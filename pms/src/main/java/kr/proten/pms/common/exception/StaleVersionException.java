package kr.proten.pms.common.exception;

/**
 * 409 STALE_VERSION — 낙관적 락 충돌 (PRD-pms §7 · AC A2-6).
 * 재확인 절차를 밟을 수 있어야 하므로 최신 값을 메시지에 실어 보낸다.
 */
public class StaleVersionException extends ConflictException {
    public StaleVersionException(String latestValues) {
        super(ErrorCode.STALE_VERSION, "다른 곳에서 먼저 변경되었습니다 — " + latestValues);
    }
}
