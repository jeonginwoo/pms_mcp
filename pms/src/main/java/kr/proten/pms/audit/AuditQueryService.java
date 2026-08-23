package kr.proten.pms.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 감사 조회 계약 — 같은 행의 두 뷰 (AC G1-3 · G2-2).
 *
 * `AuditTrail`(기록)과 인터페이스를 나눈 이유: 기록하는 모듈은 조회를 필요로 하지
 * 않고, 조회하는 쪽은 기록 권한을 가질 이유가 없다(ISP). append-only 불변식은
 * `AuditTrail`에 수정·삭제 메서드가 없다는 사실로 서 있으므로, 여기에 읽기가
 * 늘어도 그 성질은 흔들리지 않는다.
 *
 * **권한 판정은 여기 있지 않다.** 통합 로그는 "사용자/조직/권한 관리" 플래그,
 * 프로젝트별 이력은 프로젝트 가시성으로 기준이 완전히 다른데, common은 두 판정
 * 어느 쪽도 알지 못한다(person·project의 몫이다). 호출자가 판정을 끝낸 뒤 부른다.
 */
public interface AuditQueryService {

    /** 전체 이력 최신순 (G1-3의 조회 절반) — 조직·계정 변경까지 담는 유일한 뷰다. */
    Page<AuditRecord> findAll(Pageable pageable);

    /** 한 프로젝트의 이력 최신순 (G2-2의 조회 절반) — `projectId` 필터다. */
    Page<AuditRecord> findByProject(long projectId, Pageable pageable);
}
