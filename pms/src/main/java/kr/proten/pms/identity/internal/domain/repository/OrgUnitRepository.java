package kr.proten.pms.identity.internal.domain.repository;

import java.util.List;
import java.util.Optional;
import kr.proten.pms.identity.internal.domain.OrgUnit;

/**
 * 조직 노드 저장소 포트 — 구현은 infra의 JPA 어댑터.
 */
public interface OrgUnitRepository {
    OrgUnit save(OrgUnit orgUnit);

    Optional<OrgUnit> findById(Long id);

    List<OrgUnit> findAll();
}
