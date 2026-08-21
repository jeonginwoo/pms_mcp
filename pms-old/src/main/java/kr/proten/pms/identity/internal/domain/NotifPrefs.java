package kr.proten.pms.identity.internal.domain;

/**
 * 알림 수신 설정 4종 (H1-4 — 적재·푸시 시 수신자 필터 F1-5).
 */
public record NotifPrefs(boolean progress, boolean project, boolean org, boolean weekly) {

    /** 기본값 — 전부 수신. */
    public static NotifPrefs allOn() {
        return new NotifPrefs(true, true, true, true);
    }
}
