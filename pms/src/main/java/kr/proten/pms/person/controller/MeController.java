package kr.proten.pms.person.controller;

import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.person.service.MeQueryService;
import kr.proten.pms.person.service.dto.MeView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 계정 API (PRD-pms §7 `GET /api/me`).
 * 화면이 권한 없는 버튼을 감추기 위한 정보를 여기서 받는다 — 판정은 여전히 서버가
 * 쓰기 시점에 한다(상위 PRD §4-1). 비밀번호·알림 설정(EPIC H 나머지)은 범위 밖이다.
 */
@RestController
@RequestMapping("/api/me")
class MeController {
    private final MeQueryService meQueryService;

    MeController(MeQueryService meQueryService) {
        this.meQueryService = meQueryService;
    }

    @GetMapping
    MeView me(@CallerPersonId long callerPersonId) {
        return meQueryService.me(callerPersonId);
    }
}
