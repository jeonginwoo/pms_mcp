package kr.proten.pms.person.controller;

import jakarta.validation.Valid;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import kr.proten.pms.person.service.PersonService;
import kr.proten.pms.person.controller.dto.UpdateProfileRequest;
import kr.proten.pms.person.service.dto.AccountView;
import kr.proten.pms.person.service.dto.MeView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 계정 API (PRD-pms §7 `/api/me` · AC H1-1·H1-2).
 * 화면이 권한 없는 버튼을 감추기 위한 정보를 여기서 받는다 — 판정은 여전히 서버가
 * 쓰기 시점에 한다(상위 PRD §4-1).
 *
 * <p><b>비밀번호(H1-3)와 알림 설정(H1-4)은 여기 없다</b>: `/api/me/*` 라우트는
 * <b>데이터를 가진 모듈</b>이 갖는다 — 비밀번호는 auth가, 알림 설정은 notification이
 * 자기 컨트롤러로 연다. 프로필이 person에 있는 것은 이름이 person의 것이기 때문이고,
 * 연락처는 포트로 받아 온다.
 */
@RestController
@RequestMapping("/api/me")
class MeController {
    private final PersonService personService;

    MeController(PersonService personService) {
        this.personService = personService;
    }

    @GetMapping
    ApiResponse<MeView> me(@CallerPersonId long callerPersonId) {
        return ApiResponse.ok(personService.me(callerPersonId));
    }

    /**
     * 내 계정 상세 (AC H1-1) — 수정 폼을 되채우는 값이다.
     * 알림 설정은 여기 없다: `GET /api/me/notif-prefs`가 따로 있고(H1-4) 화면도
     * 그 라우트를 쓴다 — 얹으면 같은 값의 원천이 둘이 된다.
     */
    @GetMapping("/account")
    ApiResponse<AccountView> account(@CallerPersonId long callerPersonId) {
        return ApiResponse.ok(personService.myAccount(callerPersonId));
    }

    /**
     * 내 프로필 수정 (AC H1-2) — 이름은 person, email·phone은 auth이고 한 트랜잭션이다.
     * email이 <b>나 말고</b> 누군가 쓰는 값이면 409 DUPLICATE_EMAIL이다.
     */
    @PutMapping("/profile")
    ApiResponse<AccountView> updateProfile(
            @CallerPersonId long callerPersonId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(personService.updateProfile(callerPersonId, request.toCommand()));
    }
}
