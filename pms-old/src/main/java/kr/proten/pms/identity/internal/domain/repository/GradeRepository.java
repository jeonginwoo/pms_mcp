package kr.proten.pms.identity.internal.domain.repository;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.identity.internal.domain.Grade;

/**
 * 직급 저장소 포트 — 구현은 infra의 JPA 어댑터.
 */
public interface GradeRepository {
    Grade save(Grade grade);

    Optional<Grade> findById(Long id);

    List<Grade> findAll();
}
