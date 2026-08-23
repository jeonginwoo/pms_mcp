package kr.proten.pms.maintenance.service;

import kr.proten.pms.maintenance.service.dto.ContractDetail;
import kr.proten.pms.maintenance.service.dto.ContractQuery;
import kr.proten.pms.maintenance.service.dto.ContractSummary;
import kr.proten.pms.maintenance.service.dto.SiteView;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 유지보수 계약 조회 (US-D4).
 *
 * <p><b>가시성 판정이 없다</b>: 유지보수 조회는 전사 공개이고 404 은닉도 없다
 * (AC D4-3 — 계약·이슈는 팀 경계 없는 회사 공용 자산이라는 시트·게시판 현행 계승,
 * 게이트 P에서 확인). 그래서 호출자 id를 받지 않는다 — 받으면 "판정에 쓰이나?"를
 * 읽는 사람이 매번 확인해야 한다.
 *
 * <p>계약 조회와 이슈 조회를 나눈 이유는 소비자가 다르기 때문이다(conventions §5):
 * 계약은 유지보수 탭과 {@code search_maintenance}가, 이슈는 담당자 화면과
 * {@code list_maintenance_logs}가 쓴다.
 */
public interface MaintenanceQueryService {
    /** 계약 목록 (D4-1) — keyword는 계약명·계약사·사이트명 3종 부분 일치. */
    Page<ContractSummary> search(ContractQuery query, Pageable pageable);

    /** 계약 상세 (D4-2) — 사이트·연락처·이슈 요약·원 프로젝트 링크를 함께 싣는다. */
    ContractDetail getContract(long contractId);

    /** 계약이 존재하는가 — 어댑터가 "계약 id인가 이슈 id인가"를 예외 없이 가른다. */
    boolean contractExists(long contractId);

    /** 계약명만 — 이슈가 0건인 계약의 응답에도 이름은 실려야 한다. */
    String contractName(long contractId);

    /** 계약의 사이트 목록 (§7 라우트) — 상세에도 들어 있지만 목록만 필요한 화면이 있다. */
    List<SiteView> listSites(long contractId);
}
