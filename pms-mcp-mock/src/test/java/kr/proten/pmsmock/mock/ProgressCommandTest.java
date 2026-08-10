package kr.proten.pmsmock.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.port.ToolError;
import kr.proten.pmsmock.port.dto.UpdateProgressResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgressCommandTest {

    private static final int 신현랑_관리자 = 1;
    private static final int 정태휘_부문장 = 13;
    private static final int 전세아 = 18;
    private static final int 조예아 = 19;
    private static final int SK온_EUE = 347;          // PM 전세아 (진행중 5%, version 1)
    private static final int 완료_수출입은행 = 1;      // 완료 100%
    private static final int 롯데관광 = 332;           // PM 남민준, 참여 조예아

    private MockData data;
    private InMemoryProgressCommandService service;

    @BeforeEach
    void setUp() {
        data = new MockData();
        service = new InMemoryProgressCommandService(data, new VisibilityPolicy(data));
    }

    @Test
    @DisplayName("합집합 키스톤(D-01): 팀원 그룹 전세아가 자기 PM 프로젝트 수정 가능 — 그룹은 게이트가 아니다")
    void unionKeystone() {
        UpdateProgressResult summary = service.updateProgress(전세아, SK온_EUE, 20, 1, false);
        assertThat(summary.executed()).isFalse();
        assertThat(data.projects.get(SK온_EUE).progress()).isEqualTo(5); // confirmed=false는 미실행

        UpdateProgressResult result = service.updateProgress(전세아, SK온_EUE, 20, 1, true);
        assertThat(result.executed()).isTrue();
        assertThat(data.projects.get(SK온_EUE).progress()).isEqualTo(20);
        assertThat(data.projects.get(SK온_EUE).version()).isEqualTo(2);
    }

    @Test
    @DisplayName("참여자도 진척률 수정 가능(D-02, US-A2) — 조예아가 롯데관광 수정")
    void participantCanWrite() {
        UpdateProgressResult result = service.updateProgress(조예아, 롯데관광, 30, 1, true);
        assertThat(result.executed()).isTrue();
    }

    @Test
    @DisplayName("비참여자는 403 — 부문 가시성만으로는 쓰기 불가 (정태휘 → SK온 EUE)")
    void nonParticipantForbidden() {
        assertThatThrownBy(() -> service.updateProgress(정태휘_부문장, SK온_EUE, 50, 1, true))
                .isInstanceOf(ToolError.class)
                .hasMessageContaining("[403");
    }

    @Test
    @DisplayName("전 프로젝트 관리 플래그(관리자 그룹) = PM 간주 — 신현랑은 어느 프로젝트든 수정 가능")
    void manageAllProjectsSubstitution() {
        UpdateProgressResult result = service.updateProgress(신현랑_관리자, SK온_EUE, 10, 1, true);
        assertThat(result.executed()).isTrue();
    }

    @Test
    @DisplayName("version 불일치는 409 + 최신값 동봉 (D-03 재확인 절차)")
    void staleVersion() {
        service.updateProgress(전세아, SK온_EUE, 20, 1, true); // version 1 → 2
        assertThatThrownBy(() -> service.updateProgress(전세아, SK온_EUE, 30, 1, true))
                .isInstanceOf(ToolError.class)
                .hasMessageContaining("[409 STALE_VERSION]")
                .hasMessageContaining("최신값은 20%")
                .hasMessageContaining("version: 2");
    }

    @Test
    @DisplayName("완료 상태는 409 PROJECT_COMPLETED — 재개 후 수정 안내 (D-08)")
    void completedRejected() {
        assertThatThrownBy(() -> service.updateProgress(정태휘_부문장, 완료_수출입은행, 90, 1, true))
                .isInstanceOf(ToolError.class)
                .hasMessageContaining("[409 PROJECT_COMPLETED]");
    }

    @Test
    @DisplayName("100% 저장은 상태 전이 없음 — completable 안내 (완료 전이 재설계)")
    void hundredPercentNoTransition() {
        UpdateProgressResult result = service.updateProgress(전세아, SK온_EUE, 100, 1, true);
        assertThat(result.completable()).isTrue();
        assertThat(data.projects.get(SK온_EUE).status()).isEqualTo("진행중"); // 전이 없음
        assertThat(result.summary()).contains("완료 처리");
    }

    @Test
    @DisplayName("범위 밖 percent는 422 (SC-23)")
    void percentOutOfRange() {
        assertThatThrownBy(() -> service.updateProgress(전세아, SK온_EUE, 120, 1, false))
                .isInstanceOf(ToolError.class)
                .hasMessageContaining("[422");
    }

    @Test
    @DisplayName("가시성 밖 프로젝트 쓰기는 404 은닉 — 403보다 먼저 (존재 노출 방지)")
    void hiddenProjectWrite() {
        assertThatThrownBy(() -> service.updateProgress(조예아, SK온_EUE, 50, 1, true))
                .isInstanceOf(ToolError.class)
                .hasMessageContaining("조회 가능한 범위");
    }
}
