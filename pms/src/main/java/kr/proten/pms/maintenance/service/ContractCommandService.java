package kr.proten.pms.maintenance.service;

import kr.proten.pms.maintenance.service.dto.ContractCommand;
import kr.proten.pms.maintenance.service.dto.ContractDetail;
import kr.proten.pms.maintenance.service.dto.SiteCommand;
import kr.proten.pms.maintenance.service.dto.SiteView;

/**
 * 유지보수 계약·사이트 쓰기 (US-D2).
 *
 * 조회({@link MaintenanceQueryService})와 계약을 나눈 이유는 <b>판정 축이 다르다</b>는
 * 것이다(conventions §5 — 소비자·판정축 중 하나가 다르면 계약이 갈린다). 조회는
 * 전사 공개라 화자를 아예 받지 않고(D4-3), 쓰기는 "계약 관리" 플래그가 없으면
 * 403이라 화자가 첫 인자다(D2-3). 한 인터페이스에 합치면 어떤 메서드가 화자를
 * 판정에 쓰는지 읽는 사람이 매번 확인해야 한다. 감사 모듈이 {@code AuditTrail}과
 * {@code AuditQueryService}를 같은 이유로 나눠 둔 선례가 있다.
 *
 * <b>삭제가 없는 것은 누락이 아니다</b>: 계약 종료는 상태 {@code 종료}로 표현한다
 * (D2-2 — 연 단위 갱신 이력 보존). 사이트 삭제도 AC에 없어 만들지 않았다.
 *
 * 이 계약은 모듈 루트가 아니라 {@code service/}에 있다 — 밖에서 부르는 모듈이
 * 없으므로 "모듈 루트 = 밖으로 나가는 전부"(§0)에 따르면 여기가 제자리다.
 * 유지보수 쓰기는 웹 화면만의 입구다(원칙 5 — 쓰기 도구는 {@code update_progress} 하나).
 */
public interface ContractCommandService {

    /**
     * 계약을 직접 등록한다 (AC D2-1) — {@code sourceProjectId}는 비어 있다.
     * 이관(D1)과 직접 등록이 입구 2개이고, 이관은 project 경로가 든다.
     */
    ContractDetail create(long callerPersonId, ContractCommand command);

    /**
     * 계약을 수정한다 (AC D2-2) — {@code version}이 어긋나면 409 STALE_VERSION.
     */
    ContractDetail update(
            long callerPersonId, long contractId, ContractCommand command, long version);

    /** 계약에 사이트를 추가한다 (AC D2-4) — 연락처를 함께 만든다. */
    SiteView addSite(long callerPersonId, long contractId, SiteCommand command);

    /**
     * 사이트를 수정한다 (AC D2-4) — 연락처는 전체 교체다({@code SiteCommand} 주석).
     * {@code version}이 어긋나면 409 STALE_VERSION.
     */
    SiteView updateSite(long callerPersonId, long siteId, SiteCommand command, long version);
}
