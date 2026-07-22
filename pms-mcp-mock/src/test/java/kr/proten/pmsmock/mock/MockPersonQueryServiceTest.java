package kr.proten.pmsmock.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.proten.pmsmock.port.PersonQueryPort;
import kr.proten.pmsmock.port.PersonSummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 사람 검색 목업 구현 단위 테스트.
 */
class MockPersonQueryServiceTest {
    // 순수 단위 테스트 — 스프링 컨텍스트 없이 직접 생성
    private final PersonQueryPort port = new MockPersonQueryService();

    @Test
    @DisplayName("이름 부분 일치로 검색한다")
    void findVisible_이름부분일치() {
        List<PersonSummaryDto> result = port.findVisible("남", null);

        assertThat(result).extracting(PersonSummaryDto::name)
                .containsExactlyInAnyOrder("남도린", "남민준");
    }

    @Test
    @DisplayName("팀명으로 검색한다")
    void findVisible_팀필터() {
        List<PersonSummaryDto> result = port.findVisible(null, "AX솔루션개발2팀");

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("이름과 팀을 동시에 지정하면 둘 다 만족하는 사람만 반환한다")
    void findVisible_이름과팀동시() {
        List<PersonSummaryDto> result = port.findVisible("태휘", "AX솔루션사업부");

        assertThat(result).extracting(PersonSummaryDto::name)
                .containsExactlyInAnyOrder("정태휘", "권태휘");
    }

    @Test
    @DisplayName("조건을 모두 생략하면 전체를 반환한다")
    void findVisible_모두생략_전체반환() {
        List<PersonSummaryDto> result = port.findVisible(null, null);

        assertThat(result).hasSize(9);
    }

    @Test
    @DisplayName("일치하는 사람이 없으면 빈 목록을 반환한다")
    void findVisible_불일치_빈목록() {
        List<PersonSummaryDto> result = port.findVisible("존재하지않음", null);

        assertThat(result).isEmpty();
    }
}
