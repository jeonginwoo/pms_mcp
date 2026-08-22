package kr.proten.pms.auth.service.impl;

import kr.proten.pms.auth.repository.UserRepository;
import kr.proten.pms.auth.service.entity.User;
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
    public long count() {
        return userRepository.count();
    }
}
