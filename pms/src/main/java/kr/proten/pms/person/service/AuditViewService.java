package kr.proten.pms.person.service;

import kr.proten.pms.audit.AuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 통합 감사 로그 조회 — AC G1-3.
 *
 * 이 계약이 common이 아니라 person에 있는 이유: 판정자가 권한 그룹의
 * "사용자/조직/권한 관리" 플래그이고 그 지식은 person의 것이다. common은 모든
 * 모듈이 의존하는 아래층이라 person을 되돌아 참조할 수 없다(순환).
 * common의 `AuditQueryService`는 권한을 모르는 순수 조회이고, 여기서 판정을 얹는다.
 */
public interface AuditViewService {

    /** 전체 이력 최신순 — 플래그가 없으면 403이다 (G1-3). */
    Page<AuditRecord> listAll(long callerPersonId, Pageable pageable);
}
