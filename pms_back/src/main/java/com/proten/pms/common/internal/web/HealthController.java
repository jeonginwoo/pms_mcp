package com.proten.pms.common.internal.web;

import com.proten.pms.common.internal.application.HealthQueryService;
import com.proten.pms.common.internal.application.HealthStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 헬스체크 REST 어댑터. 판정은 애플리케이션 서비스에 위임한다.
 */
@RestController
@RequiredArgsConstructor
public class HealthController {
    // 헬스 판정 유스케이스
    private final HealthQueryService healthQueryService;

    /**
     * FE-BE-DB 연결 상태를 반환합니다. DB 왕복 실패 시 503으로 응답합니다.
     */
    @GetMapping("/api/health")
    public ResponseEntity<HealthStatus> health() {
        HealthStatus status = healthQueryService.check();

        if (!status.database()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status);
        }

        return ResponseEntity.ok(status);
    }
}
