package kr.proten.pms.project.service.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.LocalDate;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.StaleVersionException;
import kr.proten.pms.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 프로젝트 엔티티 불변식 단위 테스트.
 * 생성 시 상태는 계약대기이고(AC A1-1) 진척률 저장 자체는 상태를 바꾸지 않는다
 * (PRD-pms §5 자동 전이 폐지 — AC A2-3). 전이 규칙(A5-1·A5-2·A7-1~A7-4)도 여기
 * 엔티티가 갖는다 — 규칙이 서비스로 새면 입구마다 다른 상태 기계가 생긴다.
 */
class ProjectTest {
    @Test
    @DisplayName("생성 — 상태=계약대기, 진척률 0, 삭제 아님")
    void create_startsAsContractPending() {
        Project project = ProjectFixtures.project(1L, "(주)가온아이", "포털 재구축", 13L);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CONTRACT_PENDING);
        assertThat(project.getProgress()).isZero();
        assertThat(project.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("생성 — 중복 판정용 정규화 값이 함께 저장된다")
    void create_storesNormalizedKey() {
        Project project = ProjectFixtures.project(1L, " GAONI ", "Portal  ReBuild", 13L);

        assertThat(project.getNormalizedClient()).isEqualTo("gaoni");
        assertThat(project.getNormalizedName()).isEqualTo("portal rebuild");
    }

    @Test
    @DisplayName("진척률 100 저장 — 상태는 그대로 진행중 (자동 전이 없음)")
    void updateProgress_hundred_keepsStatus() {
        Project project = inProgress(90);

        project.updateProgress(100);

        assertThat(project.getProgress()).isEqualTo(100);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("완료 상태 판정 — 진척률 수정 거절(A2-8)의 근거")
    void isCompleted_reflectsStatus() {
        assertThat(completed().isCompleted()).isTrue();
    }

    @Test
    @DisplayName("A5-1 — 순방향 한 칸 전이는 통과한다 (계약대기→수주확정→진행중)")
    void advanceStatusTo_forwardOneStep_changesStatus() {
        Project project = ProjectFixtures.project(1L, "(주)가온아이", "포털 재구축", 13L);

        project.advanceStatusTo(ProjectStatus.ORDER_CONFIRMED);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ORDER_CONFIRMED);

        project.advanceStatusTo(ProjectStatus.IN_PROGRESS);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("A5-1 — 같은 상태를 주면 아무 일도 일어나지 않는다 (정보만 수정)")
    void advanceStatusTo_sameStatus_isNoOp() {
        Project project = inProgress(50);

        project.advanceStatusTo(ProjectStatus.IN_PROGRESS);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("A5-2 — 건너뛰기 전이는 409 INVALID_TRANSITION")
    void advanceStatusTo_skipping_isConflict() {
        Project project = ProjectFixtures.project(1L, "(주)가온아이", "포털 재구축", 13L);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> project.advanceStatusTo(ProjectStatus.IN_PROGRESS))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_TRANSITION));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CONTRACT_PENDING);
    }

    @Test
    @DisplayName("A5-2 — 역방향 전이는 409 INVALID_TRANSITION")
    void advanceStatusTo_backward_isConflict() {
        Project project = inProgress(50);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> project.advanceStatusTo(ProjectStatus.ORDER_CONFIRMED))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_TRANSITION));
    }

    @Test
    @DisplayName("A5-1 — 완료·유지보수중으로는 이 경로로 갈 수 없다 (전용 경로 전용)")
    void advanceStatusTo_completedOrMaintenance_isConflict() {
        Project project = inProgress(100);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> project.advanceStatusTo(ProjectStatus.COMPLETED));
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> project.advanceStatusTo(ProjectStatus.UNDER_MAINTENANCE));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("A7-1 — 진행중·100%는 완료로 전이한다")
    void complete_inProgressAtHundred_becomesCompleted() {
        Project project = inProgress(100);

        project.complete();

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(project.getProgress()).isEqualTo(100);
    }

    @Test
    @DisplayName("A7-2 — 진척률 100 미만은 409 PROGRESS_INCOMPLETE, 아무것도 안 바뀐다")
    void complete_belowHundred_isConflict() {
        Project project = inProgress(90);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(project::complete)
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.PROGRESS_INCOMPLETE));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("A7-4 — 진행중이 아닌 상태의 완료 처리는 409 INVALID_TRANSITION")
    void complete_notInProgress_isConflict() {
        Project pending = ProjectFixtures.project(
                1L, "(주)가온아이", "포털 재구축", 13L, ProjectStatus.ORDER_CONFIRMED, 100, 1L);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(pending::complete)
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_TRANSITION));
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(completed()::complete);
    }

    @Test
    @DisplayName("A7-3 — 재개는 진행중으로 되돌리고 진척률을 90으로 리셋한다")
    void reopen_completed_returnsToInProgressAtNinety() {
        Project project = completed();

        project.reopen();

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(project.getProgress()).isEqualTo(90);
    }

    @Test
    @DisplayName("A7-4 — 완료가 아닌 상태(유지보수중 포함)의 재개는 409 INVALID_TRANSITION")
    void reopen_notCompleted_isConflict() {
        Project maintenance = ProjectFixtures.project(
                1L, "(주)가온아이", "포털 재구축", 13L, ProjectStatus.UNDER_MAINTENANCE, 100, 6L);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(maintenance::reopen)
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.INVALID_TRANSITION));
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(inProgress(100)::reopen);
    }

    @Test
    @DisplayName("A5-1 — 정보 수정은 정규화 값을 갱신하고 PM은 건드리지 않는다")
    void editInfo_updatesNormalizedKeyAndKeepsManager() {
        Project project = inProgress(50);

        project.editInfo(
                new ProjectKey("새고객사", "새  이름"),
                "AI",
                Engagement.ONSITE,
                3.5,
                ProjectFixtures.START,
                ProjectFixtures.END);

        assertThat(project.getClient()).isEqualTo("새고객사");
        // 표시값은 원본 그대로, 중복 판정용 값만 정규화된다 (ProjectKeyTest와 같은 규칙)
        assertThat(project.getName()).isEqualTo("새  이름");
        assertThat(project.getNormalizedName()).isEqualTo("새 이름");
        assertThat(project.getEngagement()).isEqualTo(Engagement.ONSITE);
        assertThat(project.getContractMm()).isEqualTo(3.5);
        assertThat(project.getManagerId()).isEqualTo(13L);
    }

    @Test
    @DisplayName("기간 규칙 — 종료일이 시작일보다 앞이거나 같으면 400 (2026-08-22)")
    void create_endDateNotAfterStart_isValidationError() {
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> Project.create(
                        new ProjectKey("(주)가온아이", "기간 역전"), null, Engagement.REMOTE, 13L,
                        1.0, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 8, 1)))
                .satisfies(thrown -> assertThat(thrown.field()).isEqualTo("endDate"));
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> Project.create(
                        new ProjectKey("(주)가온아이", "같은 날"), null, Engagement.REMOTE, 13L,
                        1.0, ProjectFixtures.START, ProjectFixtures.START));
    }

    @Test
    @DisplayName("기간 규칙 — 한쪽만 비어 있으면 통과한다 (계약 전 단계)")
    void create_openEndedPeriod_isAllowed() {
        assertThatNoException().isThrownBy(() -> Project.create(
                new ProjectKey("(주)가온아이", "종료일 미정"), null, Engagement.REMOTE, 13L,
                1.0, ProjectFixtures.START, null));
    }

    @Test
    @DisplayName("기간 규칙 — 수정에도 같은 규칙이 걸린다")
    void editInfo_endDateNotAfterStart_isValidationError() {
        Project project = inProgress(50);

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> project.editInfo(
                        new ProjectKey("(주)가온아이", "포털 재구축"), null, Engagement.REMOTE, 1.0,
                        LocalDate.of(2026, 12, 31), LocalDate.of(2026, 8, 1)));
        assertThat(project.getEndDate()).isEqualTo(ProjectFixtures.END);
    }

    @Test
    @DisplayName("A2-6 — version 불일치는 최신 진척률·version을 담아 알린다")
    void requireVersion_mismatch_throwsWithLatestValues() {
        Project project = inProgress(90);

        assertThatExceptionOfType(StaleVersionException.class)
                .isThrownBy(() -> project.requireVersion(1L))
                .satisfies(thrown -> assertThat(thrown.getMessage()).contains("90").contains("3"));
    }

    private Project inProgress(int progress) {
        return ProjectFixtures.project(
                1L, "(주)가온아이", "포털 재구축", 13L, ProjectStatus.IN_PROGRESS, progress, 3L);
    }

    private Project completed() {
        return ProjectFixtures.project(
                1L, "(주)가온아이", "포털 재구축", 13L, ProjectStatus.COMPLETED, 100, 5L);
    }
}
