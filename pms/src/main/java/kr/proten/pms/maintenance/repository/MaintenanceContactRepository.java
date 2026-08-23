package kr.proten.pms.maintenance.repository;

import java.util.Collection;
import java.util.List;
import kr.proten.pms.maintenance.service.entity.MaintenanceContact;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사이트 담당자 연락처 저장소 — 계약 상세(D4-2)가 사이트별로 묶어 보여 준다. */
public interface MaintenanceContactRepository extends JpaRepository<MaintenanceContact, Long> {

    List<MaintenanceContact> findBySiteIdInOrderByIdAsc(Collection<Long> siteIds);
}
