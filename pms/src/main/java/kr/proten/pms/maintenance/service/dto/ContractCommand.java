package kr.proten.pms.maintenance.service.dto;

import java.time.LocalDate;
import kr.proten.pms.maintenance.service.entity.ContractStatus;

/**
 * 계약 등록·수정 입력 (AC D2-1·D2-2).
 *
 * 엔티티의 {@code ContractProfile}과 필드가 겹치지만 따로 두는 이유가 둘이다.
 *
 * 첫째, id를 받지 않는다 — 새 계약의 id는 발급 규칙(PRD-pms §4 표: 하드 삭제가
 * 없으므로 {@code max(id)+1})이 정하는 것이고 호출자가 고를 값이 아니다.
 *
 * 둘째, 시트 유래 필드({@code sheetSection}·{@code contractDateNote})가 없다.
 * 둘은 원본 시트를 보존하려고 만든 칸이라(2026-08-23 결정) 직접 등록에는 채울
 * 원문이 없다. 수정에서도 건드리지 않는다 — 적재된 원문을 화면이 덮어쓸 수 있게
 * 두면 시드 원문 보존이라는 그 칸의 목적이 사라진다.
 *
 * @param status 필수 — 표의 {@code not null}이 곧 규칙이다(§4 상태 4종)
 */
public record ContractCommand(
        String contractor,
        String name,
        ContractStatus status,
        LocalDate contractDate,
        LocalDate startDate,
        LocalDate endDate,
        Long amount,
        Long monthlyAmount,
        Long salesRepId,
        String category,
        String targetInfra,
        String regularCheck,
        String note) {
}
