package kr.proten.pms.notification.controller;

import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.common.web.PageResponse;
import kr.proten.pms.notification.NotificationService;
import kr.proten.pms.notification.NotificationView;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 API (PRD-pms §7 `/api/notifications`) — EPIC F.
 *
 * SSE 스트림(`GET /api/notifications/stream`)은 아직 라우트를 만들지 않았다:
 * 인증을 헤더로 실을 수 없어 `?access_token=` 쿼리 파라미터를 쓰기로 돼 있고(§7),
 * 그 토큰이 액세스 로그에 남지 않게 하는 마스킹까지가 한 묶음이라 적재 로직보다
 * 먼저 열면 토큰이 로그로 새는 상태가 된다.
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController {
    private final NotificationService notificationService;

    NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 내 알림 목록 (AC F1-3) — `?read=false`면 미읽음만. */
    @GetMapping
    ApiResponse<PageResponse<NotificationView>> list(
            @CallerPersonId long callerPersonId,
            @RequestParam(name = "read", required = false) Boolean read,
            Pageable pageable) {
        return ApiResponse.ok(
                PageResponse.of(notificationService.listMine(callerPersonId, read, pageable)));
    }

    @PatchMapping("/{notificationId}/read")
    ApiResponse<Void> markRead(
            @CallerPersonId long callerPersonId,
            @PathVariable long notificationId) {
        notificationService.markRead(callerPersonId, notificationId);

        return ApiResponse.ok();
    }
}
