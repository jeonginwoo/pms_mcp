package kr.proten.pms.auth.service.impl;

import kr.proten.pms.auth.repository.UserRepository;
import kr.proten.pms.auth.service.entity.User;
import kr.proten.pms.common.exception.NotFoundException;
import java.util.Optional;
import kr.proten.pms.person.AccountContact;
import kr.proten.pms.person.AccountPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * person이 정의한 계정 계약(`AccountPort`)의 auth 측 구현.
 *
 * 초기 비밀번호·해시 방식·계정 엔티티는 전부 이 모듈 안에 남는다 — person은
 * 인원 등록 시 "계정도 만들어 달라"만 말한다(AC E2-1).
 */
@Component
class AccountPortAdapter implements AccountPort {
    // 신규 계정의 초기 비밀번호 (부록 B 확정값) — 첫 로그인 후 변경 안내가 전제다
    private static final String INITIAL_PASSWORD = "proten1!";

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    AccountPortAdapter(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public void createInitialAccount(long personId, String email) {
        userRepository.save(User.of(
                userRepository.nextId(),
                personId,
                email.trim(),
                passwordHasher.hash(INITIAL_PASSWORD),
                null));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailTaken(String email) {
        return userRepository.existsByEmail(email.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountContact> contactOf(long personId) {
        return userRepository.findByPersonId(personId)
                .map(user -> new AccountContact(user.getEmail(), user.getPhone()));
    }

    /**
     * 연락처 변경 (AC H1-2) — 호출자의 트랜잭션에 참여한다({@code REQUIRED} 기본값).
     *
     * <p><b>계정이 없으면 404다</b>(2026-08-25 정정): 구 주석은 "시스템 계정처럼 로그인
     * 계정이 없는 인원이 있다"며 조용히 넘겼는데 <b>실측하면 거짓</b>이다 —
     * `seed_org_proten.sql`이 시스템 계정(person 44)에도 `admin@proten.co.kr` 행을 넣어
     * 44명 전원이 계정을 갖는다. 없는 것은 정상 상태가 아니라 데이터 이상이고,
     * 조용히 넘기면 <b>화면이 "저장했습니다"를 띄운 뒤 값이 사라진다</b>.
     */
    @Override
    @Transactional
    public void updateContact(long personId, String email, String phone) {
        User user = userRepository.findByPersonId(personId)
                .orElseThrow(NotFoundException::new);
        user.updateContact(email.trim(), phone);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailTakenByOther(long personId, String email) {
        return userRepository.existsByEmailAndPersonIdNot(email.trim(), personId);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return userRepository.count();
    }
}
