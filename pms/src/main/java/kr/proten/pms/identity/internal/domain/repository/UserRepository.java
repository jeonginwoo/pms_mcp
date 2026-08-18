package kr.proten.pms.identity.internal.domain.repository;

import java.util.Optional;
import kr.proten.pms.identity.internal.domain.User;

/**
 * 로그인 계정 저장소 포트 — 구현은 infra의 JPA 어댑터.
 */
public interface UserRepository {
    User save(User user);

    Optional<User> findByEmail(String email);

    Optional<User> findByPersonId(Long personId);
}
