package kr.proten.pms.person;

import java.util.Optional;

/**
 * 로그인 계정에 대해 person이 필요로 하는 것 — 구현은 auth 모듈이 가진다.
 *
 * 방향을 뒤집어 둔 이유: 로그인은 인원이 활성인지 person에게 물어야 하고
 * (`PersonDirectoryService`), 인력 등록(AC E2-1)은 계정을 함께 만들어야 한다.
 * 양쪽이 서로의 서비스를 직접 부르면 모듈 순환이 되어 `ModularityTest`가 막는다.
 * 그래서 **필요한 쪽(person)이 계약을 정의하고 제공하는 쪽(auth)이 구현한다** —
 * person은 auth를 import하지 않고, 의존은 auth → person 한 방향으로만 흐른다.
 *
 * 계정의 표현(엔티티·비밀번호 정책·해시 방식)은 전부 auth 안에 남는다. person은
 * "계정이 생겼다 / 이 email은 이미 쓰인다 / 계정이 하나라도 있다"만 안다.
 */
public interface AccountPort {

    /**
     * 새 인원의 로그인 계정을 만든다 (AC E2-1) — 초기 비밀번호는 auth가 정한다.
     * 호출자의 트랜잭션에 참여하므로 인원 등록이 롤백되면 계정도 남지 않는다.
     */
    void createInitialAccount(long personId, String email);

    /** 이미 쓰이는 로그인 ID인가 (AC E2-1·H1-2 — `409 DUPLICATE_EMAIL`). */
    boolean emailTaken(String email);

    /**
     * 내 계정의 연락처 (AC H1-1 `GET /api/me/account`).
     *
     * <p>빈 값은 <b>정상 상태가 아니다</b> — 실측상 44명 전원이 계정을 갖는다. 그래도
     * {@code Optional}인 것은 조회가 데이터 이상 때문에 화면을 막지 않게 하려는 것이고,
     * 쓰기({@link #updateContact})는 같은 상황을 404로 거절한다.
     */
    Optional<AccountContact> contactOf(long personId);

    /**
     * 연락처를 바꾼다 (AC H1-2) — email은 로그인 ID이므로 다음 로그인부터 그 값이다.
     *
     * <p>호출자의 트랜잭션에 참여하므로 이름 변경이 롤백되면 연락처도 남지 않는다.
     * 비밀번호는 이 경로로 바뀌지 않는다 — 그쪽은 auth가 통째로 갖는다(H1-3).
     */
    void updateContact(long personId, String email, String phone);

    /**
     * <b>나를 뺀</b> 누군가 이미 쓰는 로그인 ID인가 (AC H1-2 — `409 DUPLICATE_EMAIL`).
     *
     * <p>{@link #emailTaken}과 나뉘어 있는 이유: 등록(E2-1)은 "아무도 안 써야" 하고
     * 수정(H1-2)은 "나 말고 아무도 안 써야" 한다. 전자를 수정에 쓰면 <b>전화번호만
     * 바꾸려는 사람이 자기 email 때문에 409를 받는다</b>.
     */
    boolean emailTakenByOther(long personId, String email);

    /** 적재된 계정 수 — 시드 섹션이 비었는지 판정하는 데 쓴다(부록 B). */
    long count();
}
