package kr.proten.pms.auth.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import kr.proten.pms.auth.service.AuthService;
import kr.proten.pms.common.config.CallerPersonId;
import kr.proten.pms.common.web.ApiResponse;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 비밀번호 변경 (AC H1-3 · §7 `PUT /api/me/password`).
 *
 * <p><b>`/api/me/*`인데 auth에 있다</b>: 그 경로는 <b>데이터를 가진 모듈</b>이 갖는다 —
 * 알림 설정이 notification에 있는 것과 같은 배치다(H1-4 선례). 비밀번호는 확인도
 * 해시도 전부 auth 안의 일이라 <b>모듈 경계를 아예 넘지 않는다</b>: `AccountPort`를
 * 넓혀 person을 거치게 했다면 person이 알 이유 없는 것을 알게 됐을 것이다.
 *
 * <p>응답에 본문이 없다 — 바뀐 것을 돌려줄 것이 없고, 새 비밀번호를 되비추는 것은
 * 어느 계층에서도 하지 않는다.
 */
@RestController
class MyPasswordController {
    private final AuthService authService;

    MyPasswordController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 현재 비밀번호 확인 후 교체 (AC H1-3).
     * 불일치·형식 오류는 <b>같은 400</b>이다 — 갈라 주면 "현재 비밀번호는 맞았다"가
     * 응답으로 새어 나간다.
     */
    @PutMapping("/api/me/password")
    ApiResponse<Void> changePassword(
            @CallerPersonId long callerPersonId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(callerPersonId, request.current(), request.newPassword());

        return ApiResponse.ok();
    }

    /**
     * 비밀번호 변경 요청 (AC H1-3).
     *
     * <p>길이 검사를 애너테이션이 아니라 서비스가 하는 이유: 불일치와 형식 오류가
     * <b>같은 400으로 수렴</b>해야 하는데, 경계에서 형식만 먼저 걸러 내면 "형식은
     * 통과했다"가 응답 시점으로 갈린다. 여기서는 비어 있는지만 본다.
     */
    record ChangePasswordRequest(
            @NotBlank(message = "현재 비밀번호는 필수입니다") String current,
            @NotBlank(message = "새 비밀번호는 필수입니다") String newPassword) {
    }
}
