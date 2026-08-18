package kr.proten.pms.identity.internal.web;

import java.util.List;
import kr.proten.pms.identity.internal.application.PeopleQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인력 조회 API — 목록은 가시성 내 부분집합, 단건은 404 은닉 (부록 A /people 조회 절반).
 * ASSUMPTION: §7 라우트 표의 /api/people/{id}는 PUT/DELETE만 명시 — 인력 상세
 * 화면 대응 최소 GET을 추가한다(모듈 내부 라우트 — 협업 접점 아님).
 * 인력 CRUD(US-E1·E2)는 PMS-M1c.
 */
@RestController
class PeopleController {
    // 인력 조회 유스케이스
    private final PeopleQueryService peopleQueryService;

    PeopleController(PeopleQueryService peopleQueryService) {
        this.peopleQueryService = peopleQueryService;
    }

    @GetMapping("/api/people")
    List<PeopleQueryService.PersonSummary> list(Authentication authentication) {
        return peopleQueryService.listVisible(callerId(authentication));
    }

    @GetMapping("/api/people/{id}")
    PeopleQueryService.PersonSummary get(Authentication authentication, @PathVariable Long id) {
        return peopleQueryService.getPerson(callerId(authentication), id);
    }

    private Long callerId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
