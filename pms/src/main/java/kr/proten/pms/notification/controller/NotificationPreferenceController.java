package kr.proten.pms.notification.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.notification.NotificationPreferences;
import kr.proten.pms.notification.NotificationService;
import kr.proten.pms.notification.NotificationType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 알림 설정 (AC H1-4 · §7 `PUT /api/me/notif-prefs`).
 *
 * <p><b>경로는 `/api/me`인데 컨트롤러가 notification에 있다</b>: 경로 묶음과 모듈 소유는
 * 다른 축이다. 설정 데이터를 가진 쪽이 notification이고(F1-5 필터를 거는 곳도 여기다),
 * person의 `MeController`에 두면 person이 알림 유형을 알아야 한다.
 *
 * <p>대상은 언제나 화자 본인이다 — "남의 알림 설정"이라는 질문이 없으므로 §7의 다른
 * `/api/me` 라우트처럼 대상 지정 파라미터를 두지 않는다.
 */
@RestController
@RequestMapping("/api/me/notif-prefs")
class NotificationPreferenceController {
    private final NotificationService notificationService;

    NotificationPreferenceController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 유형 전체를 담아 돌려준다 — 화면이 토글을 그리려면 무엇을 끌 수 있는지 알아야 한다. */
    @GetMapping
    ApiResponse<NotificationPreferences> get(@CallerPersonId long callerPersonId) {
        return ApiResponse.ok(notificationService.myPreferences(callerPersonId));
    }

    /** 전체 교체 (PUT 의미론) — 빠진 유형은 켜짐으로 본다. */
    @PutMapping
    ApiResponse<NotificationPreferences> update(
            @CallerPersonId long callerPersonId,
            @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(
                notificationService.updatePreferences(callerPersonId, request.enabled()));
    }

    /**
     * 유형별 on/off.
     *
     * <p>구 H1-4 문구의 `{progress, project, org, weekly}` 네 칸을 쓰지 않는다 —
     * 알림 유형이 정해지기 전의 이름이고 넷 중 `project` 말고는 대응하는 알림이 없다
     * (2026-08-24 확정 — PRD-pms §6 H1-4 함께 정정).
     */
    record UpdateRequest(@NotNull Map<NotificationType, Boolean> enabled) {
    }
}
