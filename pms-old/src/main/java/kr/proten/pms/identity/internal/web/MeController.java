package kr.proten.pms.identity.internal.web;

import kr.proten.pms.common.CallerPersonId;
import kr.proten.pms.identity.internal.application.MeQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 정보 API (H1-1 — MCP whoami와 같은 서비스).
 * ASSUMPTION: M1a는 인증 관통 확인용 최소 응답 — 완성(조직 경로·그룹명)은 PMS-M1d.
 */
@RestController
class MeController {
    // 내 정보 조회 유스케이스
    private final MeQueryService meQueryService;

    MeController(MeQueryService meQueryService) {
        this.meQueryService = meQueryService;
    }

    @GetMapping("/api/me")
    MeQueryService.MeSummary me(@CallerPersonId Long callerId) {
        return meQueryService.getMe(callerId);
    }
}
