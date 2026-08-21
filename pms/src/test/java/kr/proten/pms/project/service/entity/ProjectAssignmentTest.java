package kr.proten.pms.project.service.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.StaleVersionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 배정 엔티티 불변식 단위 테스트 — AC B1-4·B2-1.
 * 종료는 행을 지우지 않는다(지난 달 가동률 보존)는 것과, 이미 종료된 배정을 다시
 * 종료하는 요청은 충돌이라는 것이 이 엔티티의 규칙이다.
 */
class ProjectAssignmentTest {
    private static final long PROJECT_ID = 7L;

    @Test
    @DisplayName("생성 — 새 배정은 진행 상태다")
    void of_startsActive() {
        assertThat(active().getStatus()).isEqualTo(AssignmentStatus.ACTIVE);
    }

    @Test
    @DisplayName("B1-4 — 기간·투입 M/M을 수정하고 역할은 그대로 둔다")
    void reschedule_changesPeriodAndMmOnly() {
        ProjectAssignment assignment = active();

        assignment.reschedule(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31), 0.8);

        assertThat(assignment.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(assignment.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(assignment.getMonthlyMm()).isEqualTo(0.8);
        assertThat(assignment.getRole()).isEqualTo(ProjectRole.PARTICIPANT);
    }

    @Test
    @DisplayName("B2-1 — 종료하면 상태만 바뀌고 행은 남는다")
    void close_marksClosedAndKeepsRow() {
        ProjectAssignment assignment = active();

        assignment.close();

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.CLOSED);
        assertThat(assignment.getMonthlyMm()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("B2-1 — 이미 종료된 배정의 재종료는 409 INVALID_TRANSITION")
    void close_alreadyClosed_isConflict() {
        ProjectAssignment assignment = active();
        assignment.close();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(assignment::close)
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo("INVALID_TRANSITION"));
    }

    @Test
    @DisplayName("B1-4 — version 불일치는 최신 배정 version을 담아 알린다")
    void requireVersion_mismatch_throwsWithLatestVersion() {
        ProjectAssignment assignment =
                ProjectFixtures.assignment(1L, PROJECT_ID, 103L, ProjectRole.PARTICIPANT, 4L);

        assertThatExceptionOfType(StaleVersionException.class)
                .isThrownBy(() -> assignment.requireVersion(2L))
                .satisfies(thrown -> assertThat(thrown.getMessage()).contains("4"));
    }

    @Test
    @DisplayName("PM 배정 판정 — 종료 거절(A6-5 불변식 보호)의 근거")
    void isManager_reflectsRole() {
        assertThat(ProjectFixtures.assignment(1L, PROJECT_ID, 13L, ProjectRole.PM).isManager())
                .isTrue();
        assertThat(active().isManager()).isFalse();
    }

    private ProjectAssignment active() {
        return ProjectFixtures.assignment(1L, PROJECT_ID, 103L, ProjectRole.PARTICIPANT);
    }
}
