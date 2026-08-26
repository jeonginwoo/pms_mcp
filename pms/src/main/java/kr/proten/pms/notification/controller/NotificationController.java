package kr.proten.pms.notification.controller;

import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.common.web.PageResponse;
import kr.proten.pms.notification.service.NotificationService;
import kr.proten.pms.notification.service.dto.NotificationView;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 API (PRD-pms §7 `/api/notifications`) — EPIC F.
 *
 * <p>SSE 스트림(`GET /api/notifications/stream`)은 **2026-08-25에 열렸고** 옆
 * `controller/stream/`에 있다. 그때까지 미룬 이유는 인증을 헤더로 실을 수 없어
 * `?access_token=` 쿼리 파라미터를 쓰는데(§7), 그 토큰이 액세스 로그에 남지 않게 하는
 * 마스킹까지가 한 묶음이라 먼저 열면 토큰이 로그로 새기 때문이었다(마스킹은 배포 요구로
 * 등재됐다 — 구현 노트 §6).
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
