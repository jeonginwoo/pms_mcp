package kr.proten.pms.maintenance.service.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 이슈 상태 전이 표 (AC D3-2).
 *
 * <p>표 전체를 한 테스트에 두는 이유: 허용 4칸만 세면 "무엇이 막혀 있는가"가 빠진다.
 * 16칸을 다 훑으면 전이를 하나 열 때 이 표가 반드시 함께 움직인다.
 */
class IssueStatusTest {

    /** 상태 → 그 상태에서 갈 수 있는 곳 전부. 여기 없는 칸은 전부 거절이다. */
    private static final Map<IssueStatus, List<IssueStatus>> ALLOWED = Map.of(
            IssueStatus.RECEIVED, List.of(IssueStatus.IN_PROGRESS),
            IssueStatus.IN_PROGRESS, List.of(IssueStatus.AWAITING_CLIENT, IssueStatus.DONE),
            IssueStatus.AWAITING_CLIENT, List.of(IssueStatus.DONE),
            IssueStatus.DONE, List.of(IssueStatus.IN_PROGRESS));

    @Test
    @DisplayName("D3-2 — 접수→처리중→고객확인대기(선택)→완료 순방향과 재개 한 칸만 열려 있다")
    void onlyTheDocumentedTransitionsAreOpen() {
        for (IssueStatus from : IssueStatus.values()) {
            for (IssueStatus to : IssueStatus.values()) {
                assertThat(from.canTransitionTo(to))
                        .as("%s → %s", from, to)
                        .isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }

    @Test
    @DisplayName("D3-2 — 같은 상태로 가는 것은 전이가 아니다")
    void sameStatusIsNotATransition() {
        assertThat(Arrays.stream(IssueStatus.values()).noneMatch(s -> s.canTransitionTo(s)))
                .isTrue();
    }

    @Test
    @DisplayName("D3-4 — 완료만 닫힌 이슈다")
    void onlyDoneIsClosed() {
        assertThat(IssueStatus.RECEIVED.isOpen()).isTrue();
        assertThat(IssueStatus.IN_PROGRESS.isOpen()).isTrue();
        assertThat(IssueStatus.AWAITING_CLIENT.isOpen()).isTrue();
        assertThat(IssueStatus.DONE.isOpen()).isFalse();
    }
}
