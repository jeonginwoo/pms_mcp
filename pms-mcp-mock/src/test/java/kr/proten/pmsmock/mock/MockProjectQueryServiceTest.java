package kr.proten.pmsmock.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.proten.pmsmock.port.AssignmentDto;
import kr.proten.pmsmock.port.ProjectDto;
import kr.proten.pmsmock.port.ProjectQueryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 프로젝트 검색 목업 구현 단위 테스트.
 */
class MockProjectQueryServiceTest {
    // 순수 단위 테스트 — 스프링 컨텍스트 없이 직접 생성
    private final ProjectQueryPort port = new MockProjectQueryService();

    @Test
    @DisplayName("상태 필터로 검색한다")
    void searchVisible_상태필터() {
        List<ProjectDto> result = port.searchVisible("수주확정", null, null);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("데이터가 없는 상태 필터는 빈 목록을 반환한다")
    void searchVisible_계약대기_빈목록() {
        List<ProjectDto> result = port.searchVisible("계약대기", null, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("키워드는 프로젝트명과 고객사 양쪽에 부분 일치한다")
    void searchVisible_키워드부분일치() {
        List<ProjectDto> result = port.searchVisible(null, "가온아이", null);

        assertThat(result).extracting(ProjectDto::id).containsExactlyInAnyOrder(103L, 106L);
    }

    @Test
    @DisplayName("상태와 키워드를 조합하면 정확히 1건이 나온다")
    void searchVisible_상태와키워드조합() {
        List<ProjectDto> result = port.searchVisible("진행중", "API", null);

        assertThat(result).extracting(ProjectDto::name)
                .containsExactly("차세대 API 게이트웨이 구축");
    }

    @Test
    @DisplayName("projectId 지정 시 배정이 포함된 상세 1건을 반환한다")
    void searchVisible_projectId_상세단건() {
        List<ProjectDto> result = port.searchVisible(null, null, 101L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().assignments())
                .extracting(AssignmentDto::personName, AssignmentDto::month, AssignmentDto::mm)
                .contains(org.assertj.core.groups.Tuple.tuple("남민준", "2026-07", 0.7));
    }

    @Test
    @DisplayName("존재하지 않는 projectId는 빈 목록을 반환한다")
    void searchVisible_없는Id_빈목록() {
        List<ProjectDto> result = port.searchVisible(null, null, 999L);

        assertThat(result).isEmpty();
    }
}
