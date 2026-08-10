package kr.proten.pmsmock.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.port.ToolError;
import kr.proten.pmsmock.port.dto.ProjectDetail;
import kr.proten.pmsmock.port.dto.ProjectSummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectVisibilityTest {

    private static final int 신현랑_관리자 = 1;
    private static final int 서지람_타부문_팀원 = 4;
    private static final int 정태휘_부문장 = 13;
    private static final int 남도린_팀장 = 16;
    private static final int 조예아_팀원 = 19;
    private static final int SK온_EUE = 347;
    private static final int 대화형_데이터 = 344;

    private InMemoryProjectQueryService service;

    @BeforeEach
    void setUp() {
        MockData data = new MockData();
        service = new InMemoryProjectQueryService(data, new VisibilityPolicy(data));
    }

    @Test
    @DisplayName("팀원(SELF)은 본인 참여 프로젝트만 — 조예아는 롯데관광 1건")
    void selfScopeSeesOnlyParticipations() {
        List<ProjectSummary> result = service.searchProjects(조예아_팀원, null, null);
        assertThat(result).extracting(ProjectSummary::id).containsExactly(332);
    }

    @Test
    @DisplayName("부문장은 자기 부문 전체 — AI기술연구소 프로젝트는 안 보임")
    void divisionScope() {
        List<ProjectSummary> result = service.searchProjects(정태휘_부문장, null, null);
        assertThat(result).extracting(ProjectSummary::division)
                .containsOnly("AX솔루션사업부");
        assertThat(result).extracting(ProjectSummary::id).doesNotContain(대화형_데이터);
    }

    @Test
    @DisplayName("참여 확장(B-05): 서지람은 타 부문이지만 참여 중인 대화형 데이터가 보인다")
    void participationExtendsVisibility() {
        List<ProjectSummary> result = service.searchProjects(서지람_타부문_팀원, null, "대화형 데이터");
        assertThat(result).extracting(ProjectSummary::id).containsExactly(대화형_데이터);
    }

    @Test
    @DisplayName("가시성 밖 상세 지정은 404 은닉 — 부재 id와 같은 문구 (S-4)")
    void hiddenNotFoundIndistinguishable() {
        assertThatThrownBy(() -> service.getProjectDetail(조예아_팀원, SK온_EUE))
                .isInstanceOf(ToolError.class)
                .hasMessageContaining("조회 가능한 범위에서 해당 데이터를 찾을 수 없습니다");
        assertThatThrownBy(() -> service.getProjectDetail(조예아_팀원, 99999))
                .isInstanceOf(ToolError.class)
                .hasMessageContaining("조회 가능한 범위에서 해당 데이터를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("상세는 version 포함 (FR-AI-10) — 쓰기 준비가 조회만으로 완결")
    void detailIncludesVersion() {
        ProjectDetail detail = service.getProjectDetail(남도린_팀장, SK온_EUE);
        assertThat(detail.version()).isEqualTo(1);
        assertThat(detail.progress()).isEqualTo(5);
        assertThat(detail.pm()).isEqualTo("전세아");
    }

    @Test
    @DisplayName("쓰기 대상 모호 실험(SC-21): '한국거래소' 키워드는 진행중 3건 매칭")
    void ambiguousKeywordMatchesThree() {
        List<ProjectSummary> result = service.searchProjects(정태휘_부문장, "진행중", "한국거래소");
        assertThat(result).extracting(ProjectSummary::id).containsExactlyInAnyOrder(322, 334, 351);
    }

    @Test
    @DisplayName("상태 필터: 관리자 전사 기준 수주확정 1건·계약대기 1건")
    void statusFilter() {
        assertThat(service.searchProjects(신현랑_관리자, "수주확정", null)).hasSize(1);
        assertThat(service.searchProjects(신현랑_관리자, "계약대기", null)).hasSize(1);
    }
}
