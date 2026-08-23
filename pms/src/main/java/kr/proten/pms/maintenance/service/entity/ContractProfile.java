package kr.proten.pms.maintenance.service.entity;

import java.time.LocalDate;

/**
 * 계약 생성 입력 묶음 (VO) — {@link MaintenanceContract#of}가 받는다.
 *
 * <p>인자를 15개 나열하지 않는 이유: 같은 타입(String 6개·LocalDate 3개·Long 3개)이
 * 이어져 있어 순서가 바뀌어도 컴파일이 통과한다. 이름이 붙은 필드로 받으면 그
 * 실수가 컴파일 시점에 드러난다.
 *
 * <p>dto가 아니라 entity 패키지에 둔다 — 엔티티를 만드는 규칙의 일부이고,
 * 컨트롤러·서비스 dto와 달리 밖으로 나가지 않는다.
 */
public record ContractProfile(
        Long sourceProjectId,
        String contractor,
        String name,
        ContractStatus status,
        String sheetSection,
        LocalDate contractDate,
        String contractDateNote,
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
