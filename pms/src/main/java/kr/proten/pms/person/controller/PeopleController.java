package kr.proten.pms.person.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.person.service.PersonCommandService;
import kr.proten.pms.person.service.PersonQueryService;
import kr.proten.pms.person.service.dto.PersonRef;
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
 * 인력 조회 API — 목록은 가시성 내 부분집합, 단건은 404 은닉 (부록 A /people 조회 절반).
 *
 * 목록에 page 봉투를 쓰지 않는다: 인원은 시드 44명 규모의 참조 데이터라 화면이
 * 한 번에 받아 쓰는 쪽이 단순하다(§7의 page 봉투 규약은 프로젝트처럼 증가하는
 * 목록에 적용한다). 등록·수정(US-E1·E2-1·E2-2)은 아직 범위 밖이고, 비활성(E2-3)만 있다.
 */
@RestController
@RequestMapping("/api/people")
class PeopleController {
    private final PersonQueryService personQueryService;
    private final PersonCommandService personCommandService;

    PeopleController(
            PersonQueryService personQueryService,
            PersonCommandService personCommandService) {
        this.personQueryService = personQueryService;
        this.personCommandService = personCommandService;
    }

    /** 인력 등록 (AC E2-1) — 로그인 계정도 함께 만들어진다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PersonRef create(
            @CallerPersonId long callerPersonId,
            @Valid @RequestBody CreatePersonRequest request) {
        return personCommandService.create(callerPersonId, request.toCommand());
    }

    @GetMapping
    List<PersonRef> list(@CallerPersonId long callerPersonId) {
        return personQueryService.listVisible(callerPersonId);
    }

    /**
     * 인원 단건 조회.
     * ASSUMPTION: §7 라우트 표의 /api/people/{id}는 PUT/DELETE만 명시 — 인력 상세
     * 화면 대응 최소 GET을 추가한다(모듈 내부 라우트 — 협업 접점 아님).
     */
    @GetMapping("/{personId}")
    PersonRef get(@CallerPersonId long callerPersonId, @PathVariable long personId) {
        return personQueryService.getPerson(callerPersonId, personId);
    }

    /**
     * 인원 비활성 (AC E2-3) — 삭제가 아니라 soft 비활성이다(과거 배정·감사는 보존).
     * "사용자/조직/권한 관리" 플래그가 없으면 403, 시스템 계정·본인은 422다.
     */
    @DeleteMapping("/{personId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@CallerPersonId long callerPersonId, @PathVariable long personId) {
        personCommandService.deactivate(callerPersonId, personId);
    }
}
