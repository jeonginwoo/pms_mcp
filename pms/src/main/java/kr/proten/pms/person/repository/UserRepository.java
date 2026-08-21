package kr.proten.pms.person.repository;

import java.util.Optional;
import kr.proten.pms.person.service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 로그인 계정 저장소. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** 로그인 ID 중복 검사 (AC E2-1·H1-2 — `409 DUPLICATE_EMAIL`). */
    boolean existsByEmail(String email);

    /** 다음 계정 id — Person과 같은 이유로 id를 직접 부여한다 (AC E2-1). */
    @Query("select coalesce(max(u.id), 0) + 1 from User u")
    long nextId();
}
