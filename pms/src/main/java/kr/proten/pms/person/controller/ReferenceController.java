package kr.proten.pms.person.controller;

import java.util.List;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.person.service.ReferenceQueryService;
import kr.proten.pms.person.service.dto.ReferenceItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 직급·권한 그룹 목록 (PRD-pms §7 `GET /api/grades`·`/api/permission-groups`).
 *
 * 두 라우트를 한 컨트롤러에 두는 이유: 관리 화면의 선택 목록이라는 한 가지 쓰임을
 * 공유하고 판정도 같다. 등록·수정·삭제(US-E4·E5)가 들어오면 자원별로 나눈다.
 */
@RestController
class ReferenceController {
    private final ReferenceQueryService referenceQueryService;

    ReferenceController(ReferenceQueryService referenceQueryService) {
        this.referenceQueryService = referenceQueryService;
    }

    @GetMapping("/api/grades")
    List<ReferenceItem> grades(@CallerPersonId long callerPersonId) {
        return referenceQueryService.grades(callerPersonId);
    }

    @GetMapping("/api/permission-groups")
    List<ReferenceItem> permissionGroups(@CallerPersonId long callerPersonId) {
        return referenceQueryService.permissionGroups(callerPersonId);
    }
}
