package kr.proten.pms.notification;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 내 알림 설정 (AC H1-4) — 유형별 on/off.
 *
 * <p><b>단위가 `NotificationType`인 이유</b>(2026-08-24 확정): H1-4는
 * {@code {progress, project, org, weekly}} 네 칸으로 적혀 있었지만 그것은 알림 유형이
 * 정해지기 전의 문구다 — 넷 중 {@code project} 말고는 대응하는 알림이 없고
 * {@code weekly}는 유형이 아니라 주기다. {@link NotificationType}의 javadoc이 이미
 * "수신자의 알림 설정이 켜고 끄는 단위"라고 선언하고 있어 그쪽에 맞췄다.
 *
 * <p>응답은 <b>언제나 유형 전체</b>를 담는다 — 화면이 토글을 그리려면 "무엇을 끌 수
 * 있는가"를 알아야 하고, 그것을 클라이언트가 열거하면 유형이 늘 때 화면이 따라오지 않는다.
 */
public record NotificationPreferences(Map<NotificationType, Boolean> enabled) {

    /** 꺼진 유형 집합에서 만든다 — 나머지는 켜진 것이다(opt-out). */
    public static NotificationPreferences of(Set<NotificationType> muted) {
        Map<NotificationType, Boolean> all = new EnumMap<>(NotificationType.class);

        for (NotificationType type : NotificationType.values()) {
            all.put(type, !muted.contains(type));
        }

        return new NotificationPreferences(Map.copyOf(all));
    }
}
