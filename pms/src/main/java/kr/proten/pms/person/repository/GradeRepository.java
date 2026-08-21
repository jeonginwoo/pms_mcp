package kr.proten.pms.person.repository;

import kr.proten.pms.person.service.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

/** 직급 저장소. */
public interface GradeRepository extends JpaRepository<Grade, Long> {
}
