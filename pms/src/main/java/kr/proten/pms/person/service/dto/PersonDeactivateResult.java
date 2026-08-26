package kr.proten.pms.person.service.dto;

import java.util.List;
import kr.proten.pms.person.LiveAssignment;

/**
 * 퇴사(비활성) 처리 결과 — 무엇이 함께 끊겼는지 <b>응답 본문에 담는다</b>
 * (AC E2-3 + PRD-pms §12 ③, 2026-08-26 신설).
 *
 * <p>{@code OrgUnitMoveResult}(E1-2)와 같은 자리·같은 이유다: 처리를 막지 않으므로
 * 오류로 낼 수 없고, 알림(EPIC F)으로 보내면 <b>지금 화면에서 사람을 정리하는 사람이
 * 그것을 보지 못한다</b>. AC E2-3의 문면("200 + {@code success:true}")은 그대로다 —
 * 없던 것을 더 실어 줄 뿐 성공 판정 방식은 바뀌지 않는다.
 *
 * <p>문구를 서버가 한 번 만드는 것도 E1-2 선례다. 숫자와 목록을 함께 싣는 것은 화면이
 * 문구를 다시 쓰고 싶을 때를 위해서다.
 *
 * <p><b>PM 배정은 여기 오지 않는다</b>: PM인 채로 사라지면 프로젝트가 PM 공석이 되므로
 * (A6-5 불변식) 그 경우는 409로 거절되고 이 결과 자체가 만들어지지 않는다.
 *
 * @param closedAssignments 자동 종료된 참여자 배정 건수
 * @param projects 그 배정이 걸려 있던 프로젝트 이름 — 이름 순, 중복 없음
 */
public record PersonDeactivateResult(
        PersonSummary person,
        int closedAssignments,
        List<String> projects,
        String notice) {

    public static PersonDeactivateResult of(PersonSummary person, List<LiveAssignment> released) {
        List<String> projects = released.stream().map(LiveAssignment::projectName).distinct().toList();

        return new PersonDeactivateResult(
                person, released.size(), projects, noticeOf(released.size(), projects));
    }

    private static String noticeOf(int closed, List<String> projects) {
        if (closed == 0) {
            return null;
        }

        // 진행 중이던 일이 조용히 끊기지 않게 — 무엇이 비었는지가 후속 배정의 입력이다
        return "진행 중이던 배정 %d건을 함께 종료했습니다: %s"
                .formatted(closed, String.join(", ", projects));
    }
}
