package kr.proten.pms.auth.repository;

import java.util.Optional;
import kr.proten.pms.auth.service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 로그인 계정 저장소. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** 내 계정 (AC H1-1·H1-2·H1-3) — personId가 계정의 주인이다. */
    Optional<User> findByPersonId(Long personId);

    /** 로그인 ID 중복 검사 — 등록(E2-1)용. "아무도 안 쓴다"를 묻는다. */
    boolean existsByEmail(String email);

    /**
     * <b>나를 뺀</b> 중복 검사 (AC H1-2) — 전화번호만 바꾸려는 사람이 자기 email
     * 때문에 409를 받지 않게 한다.
     */
    boolean existsByEmailAndPersonIdNot(String email, Long personId);

    /** 다음 계정 id — Person과 같은 이유로 id를 직접 부여한다 (AC E2-1). */
    @Query("select coalesce(max(u.id), 0) + 1 from User u")
    long nextId();
}
