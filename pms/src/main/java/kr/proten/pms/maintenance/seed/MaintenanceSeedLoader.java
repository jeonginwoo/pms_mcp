package kr.proten.pms.maintenance.seed;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.proten.pms.maintenance.repository.MaintenanceContactRepository;
import kr.proten.pms.maintenance.repository.MaintenanceContractRepository;
import kr.proten.pms.maintenance.repository.MaintenanceIssueRepository;
import kr.proten.pms.maintenance.repository.MaintenanceSiteRepository;
import kr.proten.pms.maintenance.service.entity.ContactParty;
import kr.proten.pms.maintenance.service.entity.ContractProfile;
import kr.proten.pms.maintenance.service.entity.ContractStatus;
import kr.proten.pms.maintenance.service.entity.IssueProfile;
import kr.proten.pms.maintenance.service.entity.IssueStatus;
import kr.proten.pms.maintenance.service.entity.IssueType;
import kr.proten.pms.maintenance.service.entity.MaintenanceContact;
import kr.proten.pms.maintenance.service.entity.MaintenanceContract;
import kr.proten.pms.maintenance.service.entity.MaintenanceIssue;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import kr.proten.pms.maintenance.service.entity.SiteChannel;
import kr.proten.pms.maintenance.service.impl.ContactParser;
import kr.proten.pms.person.PersonDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 유지보수 시드 적재 (부록 B — `maintenance.json` 계약 105 · 사이트 157 · 이슈 14).
 *
 * <p>적재 시 보정하고 <b>원본 JSON은 수정하지 않는다</b> — 시트를 다시 내려받아도
 * 규칙이 살아남는다(projects.json의 OFFSITE·진척률 보정과 같은 형태):
 * <ul>
 *   <li>계약 상태 {@code 자동연장}·{@code 갱신} 2건 → {@code 유지}, 원문은 note에
 *       (2026-08-23 결정. 모델·MCP 도구가 4종이고 둘 다 실제로 유지 중인 계약이다)
 *   <li>{@code serverSpec}은 사이트로 내린다 — 계약 행에 적혀 있지만 값이 사이트
 *       하나를 가리킨다("태광그룹- 1번서버 …"). 접두가 사이트명과 겹치면 그 사이트에,
 *       사이트가 하나뿐인 계약이면 그 사이트에 붙인다
 *   <li>{@code salesRep}은 이름 문자열이라 person에 이름으로 물어 id로 바꾼다
 *       (정확히 한 명일 때만 — 동명이인이면 비운다)
 *   <li>연락처는 원문을 보존하고 전화·이메일만 파싱한다
 * </ul>
 *
 * <p>이슈는 <b>계약명·계약사·사이트명 3종 일치</b>로 사이트에 붙인다(2026-08-14 확정).
 * 붙지 않는 이슈는 {@code siteId=null}로 그대로 적재한다 — 시드 14건 중 7건이 그
 * 상태이고(태그가 프로젝트 고객사를 가리킨다) 버리면 원본 이력이 사라진다.
 */
@Component
@Order(MaintenanceSeedLoader.ORDER)
class MaintenanceSeedLoader implements ApplicationRunner {
    /** 인원 시드(0)·프로젝트 시드(100) 뒤 — 엔지니어·영업대표가 실재해야 한다. */
    static final int ORDER = 200;

    private static final String SEED_FILE = "maintenance.json";

    private static final Logger log = LoggerFactory.getLogger(MaintenanceSeedLoader.class);

    private final MaintenanceContractRepository contractRepository;
    private final MaintenanceSiteRepository siteRepository;
    private final MaintenanceContactRepository contactRepository;
    private final MaintenanceIssueRepository issueRepository;
    private final PersonDirectoryService personDirectoryService;
    private final ContactParser contactParser;
    private final ObjectMapper objectMapper;
    private final String seedPath;

    MaintenanceSeedLoader(
            MaintenanceContractRepository contractRepository,
            MaintenanceSiteRepository siteRepository,
            MaintenanceContactRepository contactRepository,
            MaintenanceIssueRepository issueRepository,
            PersonDirectoryService personDirectoryService,
            ContactParser contactParser,
            ObjectMapper objectMapper,
            @Value("${pms.seed.path:}") String seedPath) {
        this.contractRepository = contractRepository;
        this.siteRepository = siteRepository;
        this.contactRepository = contactRepository;
        this.issueRepository = issueRepository;
        this.personDirectoryService = personDirectoryService;
        this.contactParser = contactParser;
        this.objectMapper = objectMapper;
        this.seedPath = seedPath;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seedPath.isBlank()) {
            log.info("유지보수 시드 적재 비활성 — pms.seed.path 미설정");

            return;
        }

        if (contractRepository.count() > 0) {
            log.info("유지보수 시드 적재 생략 — 이미 계약 {}건 있다", contractRepository.count());

            return;
        }

        Path seedFile = Path.of(seedPath, SEED_FILE);

        if (!Files.isReadable(seedFile)) {
            log.warn("유지보수 시드 파일을 읽을 수 없다 — {}", seedFile.toAbsolutePath());

            return;
        }

        load(read(seedFile));
    }

    private void load(SeedFile seed) {
        SiteIndex index = new SiteIndex();
        int statusFixed = 0;
        int contacts = 0;

        for (SeedContract record : seed.contracts()) {
            if (record.needsStatusFix()) {
                statusFixed++;
            }

            MaintenanceContract contract =
                    contractRepository.save(toContract(record, salesRepIdOf(record)));
            List<MaintenanceSite> sites = saveSites(record, contract.getId());
            index.add(sites);
            contacts += saveContacts(record, sites);
        }

        int linked = saveIssues(seed.issues(), index);

        log.info("유지보수 시드 적재 완료 — 계약 {}건 · 사이트 {}건 · 연락처 {}건 · 이슈 {}건"
                        + "(사이트 연결 {}건). 보정: 상태 → 유지 {}건",
                contractRepository.count(), siteRepository.count(), contacts,
                issueRepository.count(), linked, statusFixed);
    }

    private SeedFile read(Path seedFile) {
        try {
            return objectMapper.readValue(seedFile.toFile(), SeedFile.class);
        } catch (RuntimeException cause) {
            throw new IllegalStateException("유지보수 시드를 읽을 수 없다: " + seedFile, cause);
        }
    }

    private MaintenanceContract toContract(SeedContract record, Long salesRepId) {
        return MaintenanceContract.of(new ContractProfile(
                record.id(),
                null,
                record.contractor(),
                record.name(),
                record.contractStatus(),
                record.sheet(),
                SeedContract.lenientDate(record.contractDate()),
                record.contractDateNote(),
                SeedContract.lenientDate(record.startDate()),
                SeedContract.lenientDate(record.endDate()),
                record.amount(),
                record.monthlyAmount(),
                salesRepId,
                record.category(),
                record.targetInfra(),
                null,
                record.noteWithOrigins()));
    }

    /**
     * 사이트를 만들며 계약 레벨 {@code serverSpec}을 제 자리로 내린다.
     * 접두가 사이트명과 겹치면 그 사이트, 사이트가 하나면 그 사이트, 아니면 아무데도.
     */
    private List<MaintenanceSite> saveSites(SeedContract record, Long contractId) {
        List<SeedSite> seedSites = record.sites();
        Long specOwner = serverSpecOwnerIndex(record);
        List<MaintenanceSite> sites = new ArrayList<>();

        for (int i = 0; i < seedSites.size(); i++) {
            SeedSite site = seedSites.get(i);
            sites.add(MaintenanceSite.of(
                    contractId,
                    site.name(),
                    site.channelValue(),
                    specOwner != null && specOwner == i ? record.strippedServerSpec() : null,
                    site.engineerId()));
        }

        return siteRepository.saveAll(sites);
    }

    private Long serverSpecOwnerIndex(SeedContract record) {
        if (record.serverSpec() == null || record.sites().isEmpty()) {
            return null;
        }

        String prefix = record.serverSpecPrefix();

        if (prefix != null) {
            for (int i = 0; i < record.sites().size(); i++) {
                if (record.sites().get(i).name().contains(prefix)
                        || prefix.contains(record.sites().get(i).name())) {
                    return (long) i;
                }
            }
        }

        return record.sites().size() == 1 ? 0L : null;
    }

    private int saveContacts(SeedContract record, List<MaintenanceSite> sites) {
        Map<String, Long> siteIdByName = new HashMap<>();
        sites.forEach(site -> siteIdByName.put(site.getName(), site.getId()));

        List<MaintenanceContact> contacts = new ArrayList<>();

        for (SeedContact contact : record.contacts()) {
            Long siteId = siteIdByName.get(contact.site());

            // 사이트를 못 찾으면 버린다 — 연락처는 사이트에 붙는 것이라(§4) 붙일
            // 곳이 없으면 계약에 매달아 둘 자리가 없다
            if (siteId == null) {
                continue;
            }

            contacts.add(MaintenanceContact.of(
                    siteId,
                    contact.partyValue(),
                    contactParser.parse(contact.contact()),
                    contact.contact()));
        }

        // 계약 단위 고객 담당자(clientRep)는 사이트가 하나뿐일 때만 그 사이트에 붙인다
        if (record.clientRep() != null && sites.size() == 1) {
            contacts.add(MaintenanceContact.of(
                    sites.getFirst().getId(),
                    ContactParty.CLIENT,
                    contactParser.parse(record.clientRep()),
                    record.clientRep()));
        }

        return contactRepository.saveAll(contacts).size();
    }

    /** 이슈를 태그로 사이트에 붙인다 — 붙지 않아도 적재한다. */
    private int saveIssues(List<SeedIssue> issues, SiteIndex index) {
        List<MaintenanceIssue> saved = new ArrayList<>();
        int linked = 0;

        for (SeedIssue issue : issues) {
            Long siteId = index.match(issue.tags());

            if (siteId != null) {
                linked++;
            }

            saved.add(MaintenanceIssue.of(new IssueProfile(
                    issue.no(),
                    siteId,
                    issue.typeValue(),
                    issue.title(),
                    // 본문·등록자는 시드에 없다 (2026-08-26 신설분) — 구 게시판이
                    // 내용도 작성자도 남기지 않았고, 그래서 이 이슈들의 정정은
                    // 담당자나 "계약 관리" 권한자가 든다(IssueWriteGuard)
                    null,
                    issue.statusValue(),
                    issue.engineerId(),
                    null,
                    SeedContract.lenientDate(issue.date()),
                    issue.statusValue() == IssueStatus.DONE
                            ? SeedContract.lenientDate(issue.date())
                            : null)));
        }

        issueRepository.saveAll(saved);

        return linked;
    }

    private Long salesRepIdOf(SeedContract record) {
        if (record.salesRep() == null || record.salesRep().isBlank()) {
            return null;
        }

        return personDirectoryService.findIdByExactName(record.salesRep()).orElse(null);
    }
    /**
     * 이슈 태그를 사이트에 잇는 색인 — <b>사이트명 일치만</b> 인정한다(2026-08-23 결정).
     *
     * <p>기록된 링크 기준은 계약명·계약사·사이트명 3종이지만(2026-08-14) 그것은
     * "어느 <b>계약</b>인가"를 찾는 기준이다. 이슈가 갖는 것은 {@code siteId}이므로
     * (PRD-pms §4) 계약 단위 매칭으로는 <b>어느 사이트인지 정할 수 없다</b> — 실측:
     * 태그 {@code [전력거래소, 사이버다임]} 6건에서 전력거래소가 실제 고객이고
     * 사이버다임은 벤더(계약사)다. 계약사로 붙이면 그 계약사의 여러 계약 중 하나의
     * 첫 사이트에 매달리게 되는데, 그것은 모르는 것을 아는 척하는 것이다.
     *
     * <p>그래서 그 6건은 {@code siteId=null}로 남는다 — 사이트명으로 걸리는 7건
     * (한국거래소 → 계약 101)만 연결된다. 부록 B의 "미연결 실데이터 그대로 둠"과
     * host 2026-08-12 실측(7건)이 이 결과다.
     */
    private static final class SiteIndex {
        private final Map<String, Long> siteIdByName = new HashMap<>();

        void add(List<MaintenanceSite> sites) {
            sites.forEach(site -> siteIdByName.putIfAbsent(normalize(site.getName()), site.getId()));
        }

        Long match(List<String> tags) {
            for (String tag : tags) {
                Long siteId = siteIdByName.get(normalize(tag));

                if (siteId != null) {
                    return siteId;
                }
            }

            return null;
        }

        private static String normalize(String text) {
            return text.trim().toLowerCase(Locale.ROOT);
        }
    }

    // --- 시드 레코드 -------------------------------------------------------

    record SeedFile(List<SeedContract> contracts, List<SeedIssue> issues) {
    }

    record SeedContract(
            long id,
            String sheet,
            String category,
            String contractor,
            String name,
            String status,
            String contractDate,
            String contractDateNote,
            String startDate,
            String endDate,
            Long amount,
            Long monthlyAmount,
            String salesRep,
            String clientRep,
            String targetInfra,
            String serverSpec,
            String note,
            List<SeedSite> sites,
            List<SeedContact> contacts) {


        /** 모델 4종에 없는 상태인가 — '자동연장'·'갱신' 2건. */
        boolean needsStatusFix() {
            return contractStatusOrNull() == null;
        }

        ContractStatus contractStatus() {
            ContractStatus found = contractStatusOrNull();

            // 모델 밖 상태는 '유지'로 흡수한다 — 둘 다 sheet="2026 계약"의 살아있는 계약
            return found == null ? ContractStatus.ACTIVE : found;
        }

        private ContractStatus contractStatusOrNull() {
            for (ContractStatus candidate : ContractStatus.values()) {
                if (candidate.label().equals(status)) {
                    return candidate;
                }
            }

            return null;
        }

        /** 흡수한 원문을 note에 남긴다 — 무엇이 보정됐는지 데이터에서 되짚을 수 있게. */
        /**
         * 보정한 원문을 note에 남긴다 — 무엇이 바뀌었는지 데이터에서 되짚을 수 있게.
         * 상태 흡수('자동연장'·'갱신' → 유지)와 날짜 당김(2027-11-31 → 11-30) 둘 다.
         */
        String noteWithOrigins() {
            List<String> origins = new ArrayList<>();

            if (needsStatusFix()) {
                origins.add("시트 계약상태 원문: " + status);
            }

            if (isOutOfRangeDate(endDate)) {
                origins.add("시트 종료일 원문: " + endDate.trim());
            }

            if (origins.isEmpty()) {
                return note;
            }

            String appended = String.join(" / ", origins);

            return note == null || note.isBlank() ? appended : note + " / " + appended;
        }


        /**
         * 날짜를 문자열로 받아 <b>느슨하게</b> 읽는다: 시트에 유효하지 않은 날짜가
         * 있다(계약 #72 {@code endDate="2027-11-31"} — 11월은 30일까지). 엔티티 타입으로
         * 직접 매핑하면 그 한 칸 때문에 기동이 선다.
         *
         * <p>보정 규칙(2026-08-23 결정): 일(day)이 그 달의 범위를 넘으면 <b>그 달의
         * 말일</b>로 당긴다 — 31을 적은 사람은 "그 달 말"을 뜻한 것이고, null로 두면
         * 연·월 정보까지 통째로 잃어 종료일 정렬·필터에서 빠진다. 무엇이 보정됐는지는
         * note에 남는다.
         */
        static LocalDate lenientDate(String text) {
            if (text == null) {
                return null;
            }

            String trimmed = text.trim();

            // "YYYY-MM-DD" 열 자리만 받는다 — 그 밖의 표기는 contractDateNote의 몫이다
            if (trimmed.length() != 10 || trimmed.charAt(4) != '-' || trimmed.charAt(7) != '-') {
                return null;
            }

            try {
                YearMonth yearMonth = YearMonth.of(
                        Integer.parseInt(trimmed.substring(0, 4)),
                        Integer.parseInt(trimmed.substring(5, 7)));

                return yearMonth.atDay(
                        Math.min(Integer.parseInt(trimmed.substring(8)), yearMonth.lengthOfMonth()));
            } catch (NumberFormatException | java.time.DateTimeException cause) {
                return null;
            }
        }

        /** 보정이 일어났는가 — note에 원문을 남길지 판단한다. */
        static boolean isOutOfRangeDate(String text) {
            LocalDate parsed = lenientDate(text);

            return parsed != null && !parsed.toString().equals(text.trim());
        }

        /** "태광그룹- 1번서버 …"의 접두 — 사이트를 찾는 열쇠다. */
        String serverSpecPrefix() {
            if (serverSpec == null) {
                return null;
            }

            int dash = serverSpec.indexOf('-');

            return dash <= 0 ? null : serverSpec.substring(0, dash).trim();
        }

        /** 사이트에 붙일 때는 접두를 뗀다 — 사이트가 이미 자기 이름을 안다. */
        String strippedServerSpec() {
            String prefix = serverSpecPrefix();

            if (prefix == null) {
                return serverSpec;
            }

            return serverSpec.substring(serverSpec.indexOf('-') + 1).trim();
        }

        @Override
        public List<SeedSite> sites() {
            return sites == null ? List.of() : sites;
        }

        @Override
        public List<SeedContact> contacts() {
            return contacts == null ? List.of() : contacts;
        }
    }

    record SeedSite(String name, String channel, Long engineerId) {
        SiteChannel channelValue() {
            if (channel == null || channel.isBlank()) {
                return null;
            }

            for (SiteChannel candidate : SiteChannel.values()) {
                if (candidate.label().equalsIgnoreCase(channel)) {
                    return candidate;
                }
            }

            return null;
        }
    }

    record SeedContact(String site, String party, String contact) {
        ContactParty partyValue() {
            for (ContactParty candidate : ContactParty.values()) {
                if (candidate.label().equals(party)) {
                    return candidate;
                }
            }

            return ContactParty.CLIENT;
        }
    }

    record SeedIssue(
            long no,
            List<String> tags,
            String title,
            String type,
            Long engineerId,
            String date,
            String status) {

        IssueType typeValue() {
            for (IssueType candidate : IssueType.values()) {
                if (candidate.label().equals(type)) {
                    return candidate;
                }
            }

            throw new IllegalStateException("시드의 알 수 없는 이슈 유형: " + type);
        }

        IssueStatus statusValue() {
            for (IssueStatus candidate : IssueStatus.values()) {
                if (candidate.label().equals(status)) {
                    return candidate;
                }
            }

            throw new IllegalStateException("시드의 알 수 없는 이슈 상태: " + status);
        }

        @Override
        public List<String> tags() {
            return tags == null ? List.of() : tags;
        }
    }
}
