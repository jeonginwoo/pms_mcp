package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.YearMonth;
import java.util.List;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.ContractBrief;
import kr.proten.pms.maintenance.ContractIssues;
import kr.proten.pms.maintenance.MaintenanceLookupService;
import kr.proten.pms.maintenance.repository.MaintenanceSiteRepository;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import kr.proten.pms.person.WorkforceDirectoryService;
import kr.proten.pms.person.WorkforceProfile;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.resource.OverbookedBrief;
import kr.proten.pms.resource.UtilizationBrief;
import kr.proten.pms.resource.UtilizationLookupService;
import kr.proten.pms.resource.UtilizationScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 도메인 루트 계약 관통 검증 (2026-08-23) — `/mcp` 어댑터가 실제로 쓸 면을 실 시드로 본다.
 *
 * <p>2026-08-23 채택된 승격 방식(안 ②: 도메인 계약을 루트로, 도구는 `mcp`에)에서
 * 이 계약들이 도구 응답을 <b>실제로 채울 수 있는지</b>가 관건이다. 단위 테스트로는
 * "필드가 있다"까지만 보이고, 실 시드로 부르면 값이 비는지가 드러난다.
 */
@SpringBootTest(properties = "pms.seed.path=../reference/seed")
@Testcontainers
class DomainRootContractsIntegrationTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 8);
    /** 박재완 — 시드에서 유일한 관리자(COMPANY scope) 실인원이다. */
    private static final long COMPANY_SCOPE_CALLER_ID = 1L;
    /** 윤종헌 — AX사업기획부라 billable=false다(시드 주석). 개인 지정은 그 규칙과 무관하다. */
    private static final long NON_BILLABLE_PERSON_ID = 7L;
    /** 이현창 — AX솔루션개발1팀(11) 팀장, 부문은 AX솔루션사업부(5). 팀≠부문인 화자다. */
    private static final long DELIVERY_TEAM_CALLER_ID = 17L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MaintenanceLookupService maintenance;
    @Autowired
    private WorkforceDirectoryService workforce;
    @Autowired
    private UtilizationLookupService utilization;
    @Autowired
    private MaintenanceSiteRepository siteRepository;
    @Autowired
    private PersonRepository personRepository;

    @Test
    @DisplayName("search_maintenance — 사이트명으로 계약에 도달하고 매칭 사이트를 동봉한다")
    void contractSearchFillsToolResponse() {
        List<ContractBrief> found = maintenance.searchContracts("가천대길병원", null, 50);

        assertThat(found).hasSize(1);
        ContractBrief brief = found.getFirst();
        // MCP ContractSummary의 7필드가 전부 채워지는지 — null이면 도구 응답이 빈다
        assertThat(brief.id()).isPositive();
        assertThat(brief.contractor()).isNotBlank();
        assertThat(brief.name()).isNotBlank();
        assertThat(brief.status()).isEqualTo("유지");
        assertThat(brief.startDate()).isNotNull();
        assertThat(brief.endDate()).isNotNull();
        assertThat(brief.matchedSites()).containsExactly("가천대길병원");
    }

    @Test
    @DisplayName("모르는 상태 라벨은 예외 — 조용히 '필터 없음'이 되면 틀린 답이 나간다")
    void unknownStatusLabelIsRejected() {
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> maintenance.searchContracts(null, "자동연장", 50));
    }

    @Test
    @DisplayName("list_maintenance_logs — 계약 id면 소속 이슈 전체, matched=CONTRACT")
    void logsByContractId() {
        long contractId = contractIdOfSite("한국거래소");
        ContractIssues logs = maintenance.logsOf(contractId, null, 50).orElseThrow();

        assertThat(logs.matched()).isEqualTo("CONTRACT");
        assertThat(logs.contractId()).isEqualTo(contractId);
        assertThat(logs.contractName()).isNotBlank();
        assertThat(logs.issues()).hasSize(7);
        assertThat(logs.issues()).allSatisfy(issue -> {
            assertThat(issue.type()).isIn("장애", "문의", "요청");
            assertThat(issue.title()).isNotBlank();
            assertThat(issue.receivedAt()).isNotNull();
            assertThat(issue.siteName()).isEqualTo("한국거래소");
            // 시드에 코멘트 본문이 없어 빈 목록이다 — null이 아니어야 한다
            assertThat(issue.comments()).isNotNull().isEmpty();
        });
    }

    @Test
    @DisplayName("이슈 id는 이제 계약 id와 겹치지 않는다 — ISSUE 갈래에 도달한다")
    void issueIdReachesIssueBranch() {
        // 시드 원본 번호(230~496)를 쓰기 전에는 이슈 id가 1~14라 계약 id에 전부 가려
        // ISSUE 갈래가 죽어 있었다(2026-08-23 발견·해소)
        ContractIssues logs = maintenance.logsOf(230L, null, 50).orElseThrow();

        assertThat(logs.matched()).isEqualTo("ISSUE");
        assertThat(logs.issues()).hasSize(1);
        assertThat(logs.issues().getFirst().id()).isEqualTo(230L);
    }

    @Test
    @DisplayName("계약이 우선이다 — 계약 id는 계약으로 해석한다(목업과 같은 순서)")
    void contractTakesPrecedence() {
        ContractIssues logs = maintenance.logsOf(101L, null, 50).orElseThrow();

        assertThat(logs.matched()).isEqualTo("CONTRACT");
        assertThat(logs.contractId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("계약도 이슈도 아닌 id는 빈 값 — 404 문구는 어댑터가 정한다")
    void unknownIdIsEmpty() {
        assertThat(maintenance.logsOf(999_999L, null, 50)).isEmpty();
    }

    @Test
    @DisplayName("WorkforceProfile — 조직 id 2종이 이름과 같은 노드를 가리킨다")
    void workforceCarriesOrgIds() {
        Person someone = personRepository.findAll().stream()
                .filter(person -> !person.isSystem())
                .findFirst()
                .orElseThrow();
        WorkforceProfile profile =
                workforce.findProfiles(List.of(someone.getId())).getFirst();

        assertThat(profile.teamOrgUnitId()).isEqualTo(someone.getOrgUnitId());
        assertThat(profile.divisionOrgUnitId()).isPositive();
        assertThat(profile.team()).isNotBlank();
        assertThat(profile.division()).isNotBlank();
        // 챗의 scope=MY_TEAM|DIVISION이 이 id로 subtree를 뽑는다
        assertThat(workforce.findPersonIdsInSubtree(profile.divisionOrgUnitId()))
                .contains(someone.getId());
    }

    @Test
    @DisplayName("get_utilization scope=PERSON — 9필드가 실 시드로 전부 채워진다")
    void utilizationFillsToolResponse() {
        // 윤종헌 182%는 개인 지정이라 billable=false와 무관하다(C1-5) — 부록 B 앵커
        List<UtilizationBrief> found = utilization.find(
                COMPANY_SCOPE_CALLER_ID, MONTH, UtilizationScope.PERSON, NON_BILLABLE_PERSON_ID);

        assertThat(found).singleElement().satisfies(brief -> {
            assertThat(brief.personId()).isEqualTo(NON_BILLABLE_PERSON_ID);
            assertThat(brief.name()).isEqualTo("윤종헌");
            assertThat(brief.team()).isNotBlank();
            assertThat(brief.division()).isNotBlank();
            assertThat(brief.month()).isEqualTo(MONTH);
            assertThat(brief.assignedMm()).isPositive();
            assertThat(brief.availableMm()).isPositive();
            assertThat(Math.round(brief.basicPct())).isEqualTo(182L);
            assertThat(brief.adjustedPct()).isPositive();
        });
    }

    @Test
    @DisplayName("scope=MY_TEAM·DIVISION — 화자만 주면 서버가 조직을 유도한다(챗 경로)")
    void scopeIsDerivedFromTheCaller() {
        // 화자를 딜리버리 조직에서 고르는 것이 이 테스트의 전제다: 전사 화자(박재완)는
        // billable=false이고(재편 후에는 회사 직속이라 팀=부문=회사다) 그 주변 인원도
        // 전원 false라 집계가 정당하게 비어, "유도가 됐는지"를 볼 수 없다
        // (2026-08-24 실측 — 첫 판이 그렇게 실패했다).
        WorkforceProfile caller =
                workforce.findProfiles(List.of(DELIVERY_TEAM_CALLER_ID)).getFirst();
        assertThat(caller.teamOrgUnitId()).isNotEqualTo(caller.divisionOrgUnitId());

        // 웹은 ?orgUnitId=를 받지만 챗은 그 값이 없다 — 이 유도가 없으면 두 scope가 막힌다
        List<UtilizationBrief> team =
                utilization.find(DELIVERY_TEAM_CALLER_ID, MONTH, UtilizationScope.MY_TEAM, null);
        List<UtilizationBrief> division =
                utilization.find(DELIVERY_TEAM_CALLER_ID, MONTH, UtilizationScope.DIVISION, null);

        // 화자 자신이 자기 팀 집계에 있다 — 엉뚱한 노드를 짚으면 이것부터 깨진다
        assertThat(team).extracting(UtilizationBrief::personId)
                .contains(DELIVERY_TEAM_CALLER_ID);
        // 팀은 부문 subtree 안에 있다. 이 화자는 팀장(TEAM scope)이라 부문 쪽이 가시성으로
        // 잘려 크기가 같을 수 있다 — 두 id를 뒤바꾸면 안 되는 것은 단위 테스트가 고정한다
        assertThat(division).extracting(UtilizationBrief::personId)
                .containsAll(team.stream().map(UtilizationBrief::personId).toList());
    }

    @Test
    @DisplayName("list_overbooked — 부록 B 2명과 그 원인 배정이 함께 나온다")
    void overbookedFillsCauses() {
        List<OverbookedBrief> overbooked =
                utilization.findOverbooked(COMPANY_SCOPE_CALLER_ID, MONTH);

        // C1-5로 윤종헌(billable=false)이 빠져 2명이다 — ProjectSeedLoadIntegrationTest와 같은 앵커
        assertThat(overbooked)
                .extracting(brief -> "%s %d%%".formatted(brief.name(), Math.round(brief.basicPct())))
                .containsExactlyInAnyOrder("이현창 191%", "김경민 133%");
        assertThat(overbooked).allSatisfy(brief -> {
            assertThat(brief.team()).isNotBlank();
            // 원인이 비면 "왜 과부하인가"에 답할 수 없다 — 과부하는 배정에서만 나온다
            assertThat(brief.causes()).isNotEmpty();
            assertThat(brief.causes()).allSatisfy(cause -> {
                assertThat(cause.projectName()).isNotBlank();
                assertThat(cause.mm()).isPositive();
            });
        });
    }

    private long contractIdOfSite(String siteName) {
        return siteRepository.findAll().stream()
                .filter(site -> site.getName().equals(siteName))
                .map(MaintenanceSite::getContractId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("시드에 없는 사이트: " + siteName));
    }
}
