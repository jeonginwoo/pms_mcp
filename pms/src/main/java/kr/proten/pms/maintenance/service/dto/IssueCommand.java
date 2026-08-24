package kr.proten.pms.maintenance.service.dto;

import kr.proten.pms.maintenance.service.entity.IssueType;

/**
 * 이슈 등록 입력 (AC D3-1) — {@code {siteId, type, 제목}}이 AC가 적은 전부다.
 *
 * <p><b>담당자를 받지 않는다</b>: D3-1이 "assigneeId 기본값 = 해당 사이트 engineerId"라고
 * 정했고 요청 모양에 그 칸이 없다. 등록자가 담당을 고르게 하면 사이트의 담당
 * 엔지니어라는 정본이 우회되고, 재배정은 D3-2가 이미 가진 경로다.
 *
 * <p><b>상태·접수일도 받지 않는다</b>: 새 이슈는 언제나 {@code 접수}이고 접수일은
 * 오늘이다. 둘을 입력으로 열면 "어제 접수된 완료 이슈"를 등록할 수 있게 되는데,
 * 그것은 이력이 아니라 이력의 위조다.
 *
 * <p>{@code siteId}는 필수다 — 엔티티의 nullable은 시드 실데이터(어느 계약에도 붙지
 * 않는 이슈 7건) 때문이고 새 이슈에는 그 사정이 없다.
 */
public record IssueCommand(Long siteId, IssueType type, String title) {
}
