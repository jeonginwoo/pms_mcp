package kr.proten.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.maintenance.repository.MaintenanceContractRepository;
import kr.proten.pms.maintenance.repository.MaintenanceSiteRepository;
import kr.proten.pms.maintenance.service.IssueQueryService;
import kr.proten.pms.maintenance.service.MaintenanceQueryService;
import kr.proten.pms.maintenance.service.dto.ContractDetail;
import kr.proten.pms.maintenance.service.dto.ContractQuery;
import kr.proten.pms.maintenance.service.dto.ContractSummary;
import kr.proten.pms.maintenance.ContractIssues;
import kr.proten.pms.maintenance.service.dto.IssueQuery;
import kr.proten.pms.maintenance.service.dto.IssueView;
import kr.proten.pms.maintenance.service.dto.SiteView;
import kr.proten.pms.maintenance.service.entity.ContractStatus;
import kr.proten.pms.maintenance.service.entity.IssueType;
import kr.proten.pms.maintenance.service.entity.MaintenanceContract;
import kr.proten.pms.maintenance.service.entity.MaintenanceIssue;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 유지보수 시드 적재·조회 관통 검증 (US-D4 · D3-4) — 실 `maintenance.json`을 실물
 * PostgreSQL에 적재하고 부록 B의 기대값과 eval 앵커가 성립하는지 본다.
 *
 * <p>여기서 값을 하는 지점은 <b>eval C류 앵커 두 개</b>다: 계약 101(한국거래소, 이슈 7건)과
 * 가천대길병원(계약 1의 45사이트 중 하나). 두 번째가 특히 중요하다 — 사용자는
 * "가천대길병원"으로 부르는데 그 문자열은 계약명·계약사에 없고 사이트명에만 있어,
 * 사이트명 매칭이 빠지면 45사이트 계약에 도달할 길이 없다(2026-08-11 결정 근거).
 *
 * <p>인원 시드·프로젝트 시드 테스트와 같은 이유로 컨테이너를 따로 띄운다.
 */
@SpringBootTest(properties = "pms.seed.path=../reference/seed")
@Testcontainers
class MaintenanceSeedLoadIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MaintenanceContractRepository contractRepository;
    @Autowired
    private kr.proten.pms.maintenance.repository.MaintenanceIssueRepository issueRepository;
    @Autowired
    private kr.proten.pms.maintenance.MaintenanceLookupService maintenance;
    @Autowired
    private MaintenanceSiteRepository siteRepository;
    @Autowired
    private MaintenanceQueryService maintenanceQueryService;
    @Autowired
    private IssueQueryService issueQueryService;
    @Autowired
    private kr.proten.pms.person.repository.PersonRepository personRepository;

    @Test
    @DisplayName("부록 B — 계약 105건 · 사이트 157건이 적재된다")
    void loadsContractsAndSites() {
        assertThat(contractRepository.count()).isEqualTo(105);
        assertThat(siteRepository.count()).isEqualTo(157);
    }

    @Test
    @DisplayName("모델 4종 — '자동연장'·'갱신' 2건이 유지로 흡수되고 원문은 note에 남는다")
    void absorbsStatusesOutsideTheModel() {
        Map<ContractStatus, Long> byStatus = contractRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        MaintenanceContract::getStatus, Collectors.counting()));

        // 시드: 유지 40 · 신규 15 · 예정 21 · 자동연장 1 · 갱신 1 · 종료 27
        assertThat(byStatus).containsExactlyInAnyOrderEntriesOf(Map.of(
                ContractStatus.ACTIVE, 42L,
                ContractStatus.NEW, 15L,
                ContractStatus.PLANNED, 21L,
                ContractStatus.ENDED, 27L));

        List<String> origins = contractRepository.findAll().stream()
                .map(MaintenanceContract::getNote)
                .filter(note -> note != null && note.contains("시트 계약상태 원문"))
                .toList();

        // 무엇이 보정됐는지 데이터에서 되짚을 수 있어야 한다
        assertThat(origins).hasSize(2);
        assertThat(String.join(" ", origins)).contains("자동연장").contains("갱신");
    }

    @Test
    @DisplayName("eval 앵커 — '가천대길병원'은 사이트명으로만 계약 1에 도달한다")
    void reachesContractThroughSiteNameOnly() {
        List<ContractSummary> found = maintenanceQueryService
                .search(new ContractQuery(null, null, null, "가천대길병원"), PageRequest.of(0, 20))
                .getContent();

        assertThat(found).hasSize(1);
        // 계약명("그룹웨어 유지보수")·계약사("㈜가온아이")에는 그 문자열이 없다
        assertThat(found.getFirst().name()).doesNotContain("가천대길병원");
        assertThat(found.getFirst().contractor()).doesNotContain("가천대길병원");
        // 무엇 때문에 걸렸는지 함께 준다 — 45사이트 계약에서 이게 없으면 오답처럼 보인다
        assertThat(found.getFirst().matchedSites()).containsExactly("가천대길병원");
        assertThat(found.getFirst().siteCount()).isEqualTo(45);
    }

    @Test
    @DisplayName("keyword가 없으면 매칭 사이트도 비어 있다 — 사이트를 훑지 않는다")
    void noKeywordMeansNoMatchedSites() {
        List<ContractSummary> found = maintenanceQueryService
                .search(ContractQuery.all(), PageRequest.of(0, 5))
                .getContent();

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(summary ->
                assertThat(summary.matchedSites()).isEmpty());
    }

    @Test
    @DisplayName("eval 앵커 — 계약 101(한국거래소)에 이슈 7건이 붙는다")
    void linksIssuesToContractOneHundredOne() {
        long contractId = contractIdOfSite("한국거래소");
        List<IssueView> issues =
                issueQueryService.listByContract(contractId, null, PageRequest.of(0, 50));

        assertThat(issues).hasSize(7);
        assertThat(issues).allSatisfy(issue -> {
            assertThat(issue.siteName()).isEqualTo("한국거래소");
            assertThat(issue.contractId()).isEqualTo(contractId);
        });
        // 접수일 내림차순 — 이슈 목록은 최근 들어온 것이 먼저다
        assertThat(issues)
                .extracting(IssueView::receivedAt)
                .isSortedAccordingTo((left, right) -> right.compareTo(left));
    }

    @Test
    @DisplayName("계약에 붙지 않는 이슈 7건도 그대로 적재된다 — 버리면 원본 이력이 사라진다")
    void keepsUnlinkedIssues() {
        List<IssueView> all = issueQueryService
                .search(IssueQuery.all(), PageRequest.of(0, 100))
                .getContent();

        assertThat(all).hasSize(14);
        // 태그가 유지보수 사이트가 아니라 프로젝트 고객사(전력거래소)를 가리키는 것들
        assertThat(all.stream().filter(issue -> issue.siteId() == null)).hasSize(7);
    }

    @Test
    @DisplayName("D3-4 — 유형 필터와 미배정 필터가 각각 다른 질문에 답한다")
    void filtersByTypeAndUnassigned() {
        long contractId = contractIdOfSite("한국거래소");

        List<IssueView> incidents =
                issueQueryService.listByContract(contractId, IssueType.INCIDENT, PageRequest.of(0, 50));

        assertThat(incidents).isNotEmpty();
        assertThat(incidents).allSatisfy(issue -> assertThat(issue.type()).isEqualTo("장애"));

        // 미배정은 "담당자로 거르지 않는다"와 다른 질문이다
        List<IssueView> unassigned = issueQueryService
                .search(new IssueQuery(null, null, null, null, true, null), PageRequest.of(0, 100))
                .getContent();

        assertThat(unassigned).allSatisfy(issue -> assertThat(issue.assignee()).isNull());
    }

    @Test
    @DisplayName("D4-2 상세 — 사이트·연락처·이슈 요약이 함께 오고 서버스펙은 사이트에 있다")
    void detailCarriesSitesContactsAndIssueCounts() {
        long contractId = contractIdOfSite("TKG태광그룹");
        ContractDetail detail = maintenanceQueryService.getContract(contractId);

        assertThat(detail.sites()).hasSize(45);
        assertThat(detail.sourceProjectId()).isNull();
        // 계약 레벨 라이선스 사양은 계약에 남는다
        assertThat(detail.targetInfra()).isEqualTo("그룹웨어");
        assertThat(detail.category()).isEqualTo("검색엔진");

        // 계약 행에 적혀 있던 서버스펙이 그 사이트로 내려갔다(2026-08-23 결정)
        SiteView taekwang = detail.sites().stream()
                .filter(site -> site.name().equals("TKG태광그룹"))
                .findFirst()
                .orElseThrow();

        assertThat(taekwang.serverSpec()).contains("1번서버").doesNotContain("태광그룹-");
        assertThat(detail.sites().stream().filter(site -> site.serverSpec() != null)).hasSize(1);
    }

    @Test
    @DisplayName("담당 엔지니어는 사이트마다 참조로 오고 미배정은 null이다")
    void sitesCarryEngineerRefsAndNullsForUnassigned() {
        long contractId = contractIdOfSite("한국거래소");
        List<SiteView> sites = maintenanceQueryService.listSites(contractId);

        assertThat(sites).isNotEmpty();
        // 시드: 2026 계약 섹션의 사이트만 실무 3명 라운드로빈, 나머지는 null
        assertThat(siteRepository.findAll().stream()
                        .filter(site -> site.getEngineerId() == null))
                .hasSize(48);
    }

    private String nameOf(long personId) {
        return personRepository.findById(personId).orElseThrow().getName();
    }

    @Test
    @DisplayName("이슈 작성 엔지니어는 시트 원본 이름 그대로 실인원에 붙는다 (2026-08-24 교정)")
    void issueWritersMatchTheirOwnNames() {
        // 변환 당시 작성자를 구 익명 명부로 매핑한 탓에(남진식→26 · 배성수→28) 인원 정본이
        // 실제 명부로 바뀌며 두 사람이 서로 뒤바뀌어 있었다 — 둘 다 CS사업팀 실인원이라
        // 화면에는 그럴듯하게 보였다. 이름이 자기 이슈에 붙는지를 여기서 고정한다.
        Map<String, Long> byWriter = issueRepository.findAll().stream()
                .map(MaintenanceIssue::getAssigneeId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(this::nameOf, Collectors.counting()));

        assertThat(byWriter).containsOnly(entry("남진식", 7L), entry("배성수", 7L));
    }

    @Test
    @DisplayName("D4-3 — 없는 계약은 404이고 가시성 판정은 없다(전사 공개)")
    void missingContractIsNotFound() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> maintenanceQueryService.getContract(999_999L));
    }

    @Test
    @DisplayName("영업대표 이름 3명이 person id로 바뀐다")
    void resolvesSalesRepNamesToPeople() {
        long withSalesRep = contractRepository.findAll().stream()
                .filter(contract -> contract.getSalesRepId() != null)
                .count();

        // 시드 salesRep은 박재완·김도한·천용우 3명이고 실제 명부에 다 있다
        assertThat(withSalesRep).isPositive();
        assertThat(maintenanceQueryService
                        .getContract(contractIdOfSite("TKG태광그룹"))
                        .salesRep())
                .isNotNull();
    }

    @Test
    @DisplayName("eval 앵커 — 계약 id가 시드 원본이다(한국거래소 = 101), 우연이 아니다")
    void contractIdsAreSeedIds() {
        // eval C-01~C-03이 계약 101을 앵커로 쓴다. identity 생성이면 시드 파일에 한 줄이
        // 끼거나 순서가 바뀌는 순간 조용히 어긋난다 — 그래서 원본 id를 명시 지정한다
        assertThat(contractIdOfSite("한국거래소")).isEqualTo(101L);
        assertThat(contractRepository.count()).isEqualTo(105);
        assertThat(contractRepository.findAll().stream().map(MaintenanceContract::getId))
                .allSatisfy(id -> assertThat(id).isBetween(1L, 105L));
    }

    @Test
    @DisplayName("이슈 id가 시드 원본 번호다(230~496) — 계약 id 공간과 겹치지 않는다")
    void issueIdsAreSeedNumbers() {
        List<Long> ids = issueRepository.findAll().stream()
                .map(MaintenanceIssue::getId)
                .sorted()
                .toList();

        assertThat(ids).hasSize(14);
        // 겹치면 list_maintenance_logs가 이슈 id를 계약으로 해석해 ISSUE 갈래가 죽는다
        assertThat(ids.getFirst()).isGreaterThan(105L);
        assertThat(ids).containsExactly(
                230L, 255L, 269L, 279L, 283L, 291L, 297L, 304L, 319L, 360L, 429L, 430L, 474L, 496L);
    }

    @Test
    @DisplayName("list_maintenance_logs — 이슈 id면 그 이슈만, matched=ISSUE")
    void logsByIssueId() {
        // 230은 계약 id 범위(1~105) 밖이라 이슈로 해석된다
        ContractIssues logs = maintenance.logsOf(230L, null, 50).orElseThrow();

        assertThat(logs.matched()).isEqualTo("ISSUE");
        assertThat(logs.issues()).hasSize(1);
        assertThat(logs.issues().getFirst().id()).isEqualTo(230L);
    }

    private long contractIdOfSite(String siteName) {
        return siteRepository.findAll().stream()
                .filter(site -> site.getName().equals(siteName))
                .map(MaintenanceSite::getContractId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("시드에 없는 사이트: " + siteName));
    }
}
