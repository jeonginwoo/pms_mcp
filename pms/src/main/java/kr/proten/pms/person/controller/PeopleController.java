package kr.proten.pms.person.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.person.controller.dto.*;
import kr.proten.pms.person.service.PersonService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인력 API — 조회·등록·수정·소속 이동·비활성 (부록 A /people).
 *
 * 목록에 page 봉투를 쓰지 않는다: 인원은 시드 44명 규모의 참조 데이터라 화면이
 * 한 번에 받아 쓰는 쪽이 단순하다(§7의 page 봉투 규약은 프로젝트처럼 증가하는
 * 목록에 적용한다). 목록은 가시성 내 부분집합이고 단건은 404 은닉이다.
 */
@RestController
@RequestMapping("/api/people")
class PeopleController {
    private final PersonService personService;

    PeopleController(PersonService personService) {
        this.personService = personService;
    }

    /** 인력 등록 (AC E2-1) — 로그인 계정도 함께 만들어진다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<PersonRef> create(
            @CallerPersonId long callerPersonId,
            @Valid @RequestBody CreatePersonRequest request) {
        return ApiResponse.ok(personService.create(callerPersonId, request.toCommand()));
    }

    @GetMapping
    ApiResponse<List<PersonRef>> list(@CallerPersonId long callerPersonId) {
        return ApiResponse.ok(personService.listVisible(callerPersonId));
    }

    /**
     * 인원 단건 조회.
     * ASSUMPTION: §7 라우트 표의 /api/people/{id}는 PUT/DELETE만 명시 — 인력 상세
     * 화면 대응 최소 GET을 추가한다(모듈 내부 라우트 — 협업 접점 아님).
     */
    @GetMapping("/{personId}")
    ApiResponse<PersonRef> get(@CallerPersonId long callerPersonId, @PathVariable long personId) {
        return ApiResponse.ok(personService.getPerson(callerPersonId, personId));
    }

    /** 인력 수정 (AC E2-2) — 권한 그룹 부여도 이 경로다(그룹은 사람의 속성이다). */
    @PutMapping("/{personId}")
    ApiResponse<PersonRef> update(
            @CallerPersonId long callerPersonId,
            @PathVariable long personId,
            @Valid @RequestBody UpdatePersonRequest request) {
        return ApiResponse.ok(personService.update(callerPersonId, request.toCommand(personId)));
    }

    /**
     * 소속 조직 이동 (AC E1-1) — 가시성이 즉시 따라 바뀐다.
     * 진행 중 배정이 있어도 막지 않는다(E1-2) — 조직 개편을 시스템이 거부하면 안 된다.
     */
    @PutMapping("/{personId}/org-unit")
    ApiResponse<PersonRef> moveOrgUnit(
            @CallerPersonId long callerPersonId,
            @PathVariable long personId,
            @Valid @RequestBody MoveOrgUnitRequest request) {
        return ApiResponse.ok(
                personService.moveOrgUnit(callerPersonId, personId, request.orgUnitId()));
    }

    /**
     * 인원 비활성 (AC E2-3) — 삭제가 아니라 soft 비활성이다(과거 배정·감사는 보존).
     * "사용자/조직/권한 관리" 플래그가 없으면 403, 시스템 계정·본인은 422다.
     */
    @DeleteMapping("/{personId}")
    ApiResponse<Void> deactivate(@CallerPersonId long callerPersonId, @PathVariable long personId) {
        personService.deactivate(callerPersonId, personId);

        return ApiResponse.ok();
    }
}
