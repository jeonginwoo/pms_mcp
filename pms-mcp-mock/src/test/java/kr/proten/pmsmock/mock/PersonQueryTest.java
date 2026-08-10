package kr.proten.pmsmock.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.port.dto.PersonSummary;
import kr.proten.pmsmock.port.dto.WhoamiResult;

import static org.assertj.core.api.Assertions.assertThat;

class PersonQueryTest {

    private static final int 서지람 = 4;
    private static final int 정태휘_부문장 = 13;
    private static final int 조예아_팀원 = 19;

    private InMemoryPersonQueryService service;

    @BeforeEach
    void setUp() {
        MockData data = new MockData();
        service = new InMemoryPersonQueryService(data, new VisibilityPolicy(data));
    }

    @Test
    @DisplayName("whoami는 권한 그룹명 반환 (결정 ⑦ — B-03: 서지람 = 팀원), 유효 권한 미반환")
    void whoamiReturnsGroupName() {
        WhoamiResult me = service.whoami(서지람);
        assertThat(me.name()).isEqualTo("서지람");
        assertThat(me.team()).isEqualTo("AI팀");
        assertThat(me.division()).isEqualTo("AI기술연구소");
        assertThat(me.permissionGroup()).isEqualTo("팀원");
    }

    @Test
    @DisplayName("find_person 가시성: 부문장은 부문 내 검색, 팀원은 본인만")
    void findPersonVisibility() {
        assertThat(service.findPeople(정태휘_부문장, "전세아", null))
                .extracting(PersonSummary::id).containsExactly(18);
        assertThat(service.findPeople(조예아_팀원, "전세아", null)).isEmpty();
        assertThat(service.findPeople(조예아_팀원, null, null))
                .extracting(PersonSummary::id).containsExactly(19);
    }
}
