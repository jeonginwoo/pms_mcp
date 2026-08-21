package kr.proten.pms.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.proten.pms.mcp.internal.seed.SeedPeople;
import kr.proten.pms.mcp.internal.seed.SeedPersonQueryService;

/**
 * 임시 시드 어댑터 검증 — 시드 44명 전량 + 인력 가시성 4단(상위 PRD §4).
 * 수치 근거: reference/seed/people.json 실측(부문 AX솔루션사업부 16명 ·
 * 팀 AX솔루션개발1팀 5명). identity 서비스로 교체 시 이 테스트도 함께 폐기.
 */
class SeedPersonQueryServiceTest {

    private final SeedPersonQueryService service = new SeedPersonQueryService(
            new SeedPeople(Path.of("../reference/seed/people.json")));

    @Test
    @DisplayName("whoami — 시드 인물·권한 그룹명(orgRole → 기본 그룹 4종) 반환")
    void whoamiReturnsSeedPersonWithGroupName() {
        var member = service.whoami(18);
        assertThat(member.name()).isEqualTo("전세아");
        assertThat(member.team()).isEqualTo("AX솔루션개발1팀");
        assertThat(member.division()).isEqualTo("AX솔루션사업부");
        assertThat(member.permissionGroup()).isEqualTo("팀원");

        assertThat(service.whoami(1).permissionGroup()).isEqualTo("관리자");
        assertThat(service.whoami(13).permissionGroup()).isEqualTo("부문장");
        assertThat(service.whoami(16).permissionGroup()).isEqualTo("팀장");
    }

    @Test
    @DisplayName("whoami — 시드에 없는 호출자는 404 은닉")
    void whoamiUnknownCallerIsConcealed() {
        assertThatThrownBy(() -> service.whoami(999))
                .isInstanceOf(ToolError.class)
                .hasMessageContaining("조회 가능한 범위에서 해당 데이터를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("find_person 가시성 — 관리자=전사 44, 부문장=부문 16, 팀장=팀 5, 팀원=본인 1")
    void findPeopleVisibilityFourTiers() {
        assertThat(service.findPeople(1, null, null)).hasSize(44);
        assertThat(service.findPeople(13, null, null)).hasSize(16)
                .allSatisfy(p -> assertThat(service.whoami(p.id()).division()).isEqualTo("AX솔루션사업부"));
        assertThat(service.findPeople(16, null, null)).hasSize(5)
                .allSatisfy(p -> assertThat(p.team()).isEqualTo("AX솔루션개발1팀"));
        List<PersonSummary> self = service.findPeople(18, null, null);
        assertThat(self).hasSize(1);
        assertThat(self.getFirst().name()).isEqualTo("전세아");
    }

    @Test
    @DisplayName("find_person 필터 — 이름·팀 부분 일치")
    void findPeopleFilters() {
        assertThat(service.findPeople(1, "세아", null))
                .singleElement().satisfies(p -> assertThat(p.name()).isEqualTo("전세아"));
        assertThat(service.findPeople(1, null, "AX솔루션개발1팀")).hasSize(5);
        // 가시성 밖 이름 검색은 필터 교집합이라 빈 결과 (팀원이 관리자 검색)
        assertThat(service.findPeople(18, "신현랑", null)).isEmpty();
    }
}
