package kr.proten.pms.maintenance.service.entity;

import java.time.LocalDate;

/**
 * 계약 수정 입력 묶음 (VO) — {@link MaintenanceContract#update}가 받는다 (AC D2-2).
 *
 * {@link ContractProfile}과 나누는 이유는 <b>바꿀 수 있는 것만 담기 때문</b>이다.
 * 생성은 id·sourceProjectId·시트 유래 필드(sheetSection·contractDateNote)까지
 * 정하지만 수정은 그 넷을 건드리지 않는다 — id는 발급 규칙의 소관이고,
 * sourceProjectId는 이관이 남긴 연결이며, 시트 유래 두 칸은 원본 보존이 목적이라
 * 화면이 덮어쓸 수 있게 두면 그 칸의 존재 이유가 사라진다.
 *
 * 전체 profile을 받고 네 필드를 무시하는 대안은 "무시된다"를 주석으로만 말하게 되고,
 * 호출자는 값을 채워 보내고도 반영되지 않는 이유를 코드에서 찾을 수 없다.
 *
 * 인자를 13개 나열하지 않는 이유는 {@code ContractProfile}과 같다 — 같은 타입이
 * 이어져 있어 순서가 바뀌어도 컴파일이 통과한다.
 */
public record ContractEdit(
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
