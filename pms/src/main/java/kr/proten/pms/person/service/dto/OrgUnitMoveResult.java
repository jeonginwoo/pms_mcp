package kr.proten.pms.person.service.dto;

import kr.proten.pms.person.PersonRef;

/**
 * 소속 이동 결과 (AC E1-1·E1-2) — 옮긴 사람 + <b>경고</b>.
 *
 * <p>{@code PersonRef}만 돌려주지 않는 이유가 E1-2다: "진행 중 배정이 있어도 허용하되
 * 경고"인데 경고를 실을 자리가 없으면 그 AC가 아무 데서도 성립하지 않는다. 이동을
 * 막지 않으므로 오류로 낼 수는 없고(허용이 규칙이다), 그렇다고 조용히 넘기면 관리자가
 * "이 사람이 진행 중인 일에 물려 있다"를 모른 채 조직을 개편한다.
 *
 * <p>{@code PersonRef}를 감싸고 바꾸지 않는다 — 그것은 모듈 루트 계약이고 project·
 * maintenance가 함께 쓴다. 경고는 이 유스케이스만의 것이다.
 *
 * <p>{@code activeAssignments}가 0이면 {@code warning}은 null이다: 경고 문구를 만드는
 * 판단을 화면마다 반복하지 않게 서버가 한 번 정한다. 숫자를 함께 싣는 것은 화면이
 * 문구를 다시 쓰고 싶을 때를 위해서다.
 */
public record OrgUnitMoveResult(PersonRef person, long activeAssignments, String warning) {

    public static OrgUnitMoveResult of(PersonRef person, long activeAssignments) {
        return new OrgUnitMoveResult(person, activeAssignments, warningOf(activeAssignments));
    }

    private static String warningOf(long activeAssignments) {
        if (activeAssignments == 0) {
            return null;
        }

        // 과거 집계가 현재 소속 기준으로 다시 계산된다는 것이 E1-2의 실제 파급이다
        return "진행 중인 배정 %d건이 있습니다 — 이동해도 배정은 유지되지만, 조직 기준 집계는 새 소속으로 계산됩니다"
                .formatted(activeAssignments);
    }
}
