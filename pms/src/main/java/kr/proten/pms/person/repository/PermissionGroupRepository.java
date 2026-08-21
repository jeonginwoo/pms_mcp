package kr.proten.pms.person.repository;

import kr.proten.pms.person.service.entity.PermissionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

/** 권한 그룹 저장소. */
public interface PermissionGroupRepository extends JpaRepository<PermissionGroup, Long> {
}
