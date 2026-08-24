package kr.proten.pms.maintenance;

import java.util.List;

/**
 * 프로젝트가 유지보수로 이관됐다 — maintenance가 발행하고 notification이 구독한다
 * (§8 · AC D1-1).
 *
 * <p>§8이 발행자를 maintenance로 적은 것이 역포트 방향과 자연스럽게 맞는다: 계약을
 * 만드는 쪽이 maintenance이므로 "이관됐다"를 말할 수 있는 것도 그쪽이다. project가
 * 발행하려면 계약 id를 되받아야 하고, 그것은 포트가 값을 돌려줄 이유를 만든다
 * ({@code HandoverPort} 주석 — 쓰지 않는 반환값을 두지 않았다).
 *
 * <p><b>계약·사이트 생성은 동기·원자적이고 알림만 fan-out이다</b>(§8 D1-1 각주).
 * 그래서 이 이벤트는 커밋 후에 돌고, 롤백되면 알림도 없다.
 *
 * <p>문구 재료를 실어 보낸다 — 구독자가 계약을 되물으면 {@code notification →
 * maintenance} 간선이 생기고, 그것은 구독 방향이 피하려던 것이다
 * ({@code MaintenanceIssueRegistered}와 같은 판단).
 *
 * <p><b>{@code siteEngineerIds}는 수신자 명단이 아니라 계약에 대한 사실이다.</b>
 * 누구에게 알릴지는 구독자가 정한다({@code OverbookingDetected}가 {@code personId}만
 * 싣고 "같은 조직의 플래그 보유자"를 구독자가 고르는 것과 같은 배치). 이 값을 싣는
 * 이유는 그것이 계약을 만든 쪽만 아는 사실이기 때문이다.
 *
 * @param handedOverBy 이관을 실행한 PM — <b>수신자가 아니라 문구의 재료</b>다.
 *                     자기가 한 일을 자기에게 알리지 않는다
 * @param siteEngineerIds 이관된 사이트들의 담당 엔지니어 (D1-1이 사이트마다 필수로
 *                     요구한 값) — 중복은 제거돼 온다
 */
public record MaintenanceHandedOver(
        long projectId,
        long contractId,
        String contractName,
        long handedOverBy,
        List<Long> siteEngineerIds) {
}
