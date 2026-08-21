package kr.proten.pms.person.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.person.service.OrgUnitService;
import kr.proten.pms.person.service.dto.OrgUnitView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조직 트리 API (PRD-pms §7 `/api/org-units`) — 관리 화면용이라 목록도 관리 권한을 요구한다.
 * 신설·이름 변경(E3-1·E3-2)은 아직 라우트를 만들지 않았다.
 */
@RestController
@RequestMapping("/api/org-units")
class OrgUnitController {
    private final OrgUnitService orgUnitService;

    OrgUnitController(OrgUnitService orgUnitService) {
        this.orgUnitService = orgUnitService;
    }

    @GetMapping
    List<OrgUnitView> list(@CallerPersonId long callerPersonId) {
        return orgUnitService.list(callerPersonId);
    }

    /** 노드 신설 (AC E3-1) — parentId 미지정은 회사(root) 생성 요청이다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrgUnitView create(
            @CallerPersonId long callerPersonId,
            @Valid @RequestBody CreateOrgUnitRequest request) {
        return orgUnitService.create(callerPersonId, request.parentId(), request.name());
    }

    /** 빈 노드 삭제 (AC E3-3) — 소속 인원·하위 노드가 있으면 409 IN_USE. */
    @DeleteMapping("/{orgUnitId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CallerPersonId long callerPersonId, @PathVariable long orgUnitId) {
        orgUnitService.delete(callerPersonId, orgUnitId);
    }
}
