package kr.proten.pms.identity.internal.seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.proten.pms.identity.internal.application.PasswordHasher;
import kr.proten.pms.identity.internal.domain.Grade;
import kr.proten.pms.identity.internal.domain.NotifPrefs;
import kr.proten.pms.identity.internal.domain.OrgUnit;
import kr.proten.pms.identity.internal.domain.PermissionGroup;
import kr.proten.pms.identity.internal.domain.Person;
import kr.proten.pms.identity.internal.domain.User;
import kr.proten.pms.identity.internal.domain.VisibilityScope;
import kr.proten.pms.identity.internal.domain.repository.GradeRepository;
import kr.proten.pms.identity.internal.domain.repository.OrgUnitRepository;
import kr.proten.pms.identity.internal.domain.repository.PermissionGroupRepository;
import kr.proten.pms.identity.internal.domain.repository.PersonRepository;
import kr.proten.pms.identity.internal.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * identity 시드 적재 — people.json 44명 + 부록 B 확정 규칙 (기본 그룹 4종 매핑·
 * 시스템 관리자 계정·billable 부문 3곳·초기 비밀번호). 기동 시 Person이 비어
 * 있으면 한 번만 적재한다(멱등). projects·maintenance 시드는 해당 도메인
 * 구현(PMS-M2·M4) 시 각 모듈이 적재한다.
 * ASSUMPTION: pms.seed.path 미설정 = 비활성 — 테스트 yml은 설정하지 않아 기존
 * 테스트 픽스처와 충돌하지 않고, 로컬 bootRun은 메인 yml 기본값으로 자동 적재
 * (부록 B "compose up 후 자동 적재" — 별도 프로파일 없이. 배포 경로는 PMS-M6).
 */
@Component
public class IdentitySeedLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(IdentitySeedLoader.class);

    // 초기 비밀번호 (2026-08-02 확정 — 최초 로그인 후 변경 안내는 M1d)
    private static final String INITIAL_PASSWORD = "proten1!";
    // 회사(root) 노드명 — 시드의 division "프로텐"(대표 직속)은 root 자신을 가리킨다
    private static final String COMPANY_NAME = "프로텐";
    // billable=false 부문 3곳 (부록 B 확정 2026-08-10 — 계 10명)
    private static final Set<String> NON_BILLABLE_DIVISIONS =
            Set.of("프로텐", "AX사업기획부", "관리•마케팅부");
    // 시스템 관리자 계정 (2026-08-09 ④ — 인력·가동률·배정 목록 제외)
    private static final String SYSTEM_ADMIN_EMAIL = "admin@proten.co.kr";

    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final GradeRepository gradeRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final PermissionGroupRepository permissionGroupRepository;
    private final PasswordHasher passwordHasher;
    // JSON 역직렬화 — Boot 관리 빈 주입 (conventions §4 "Use Boot-managed beans")
    private final ObjectMapper objectMapper;
    // 시드 디렉터리 — 빈 값이면 적재 비활성
    private final String seedPath;

    public IdentitySeedLoader(
            PersonRepository personRepository,
            UserRepository userRepository,
            GradeRepository gradeRepository,
            OrgUnitRepository orgUnitRepository,
            PermissionGroupRepository permissionGroupRepository,
            PasswordHasher passwordHasher,
            ObjectMapper objectMapper,
            @Value("${pms.seed.path:}") String seedPath) {
        this.personRepository = personRepository;
        this.userRepository = userRepository;
        this.gradeRepository = gradeRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.permissionGroupRepository = permissionGroupRepository;
        this.passwordHasher = passwordHasher;
        this.objectMapper = objectMapper;
        this.seedPath = seedPath;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seedPath.isBlank()) {
            return;
        }
        Path peopleJson = Path.of(seedPath, "people.json");
        if (!Files.exists(peopleJson)) {
            log.warn("시드 적재 스킵 — 파일 없음: {} (pms.seed.path 확인)", peopleJson.toAbsolutePath());
            return;
        }
        if (personRepository.count() > 0) {
            log.info("시드 적재 스킵 — 인원 데이터가 이미 있음");
            return;
        }

        List<SeedPersonRow> rows = readRows(peopleJson);
        Map<String, Long> gradeIds = loadGrades(rows);
        OrgUnitIds orgUnitIds = loadOrgTree(rows);
        Map<String, Long> groupIds = loadDefaultGroups();
        // 동일 초기 비밀번호라 해시 1회 재사용 (기동 시간 — BCrypt 45회 회피)
        String initialHash = passwordHasher.hash(INITIAL_PASSWORD);

        for (SeedPersonRow row : rows) {
            Person saved = personRepository.save(new Person(
                    null,
                    row.name(),
                    orgUnitIds.of(row),
                    gradeIds.get(row.grade()),
                    groupIds.get(row.orgRole()),
                    1.0,
                    !NON_BILLABLE_DIVISIONS.contains(row.division()),
                    false,
                    true,
                    0L));
            // 후속 시드(projects·maintenance)와 eval 기대값이 시드 인원 id를 참조하므로
            // 생성 id와의 정합이 깨지면 조용한 오연결 대신 즉시 실패시킨다
            if (!saved.id().equals(row.id())) {
                throw new IllegalStateException("시드 인원 id 불일치: 기대 " + row.id()
                        + " ≠ 생성 " + saved.id() + " — 빈 DB(신규 시퀀스)에만 적재 가능");
            }
            userRepository.save(new User(
                    null, saved.id(), row.email(), initialHash, null, NotifPrefs.allOn(), 0L));
        }

        // ASSUMPTION: 시스템 계정의 직급 = 대표이사 재사용 — 어떤 화면에도 노출되지
        // 않아 의미 없는 필드이나 스키마상 필수. 신설 직급은 직급 관리(E4) 목록을
        // 오염시켜 미채택
        Person systemAdmin = personRepository.save(new Person(
                null, "시스템 관리자", orgUnitIds.rootId(), gradeIds.get("대표이사"),
                groupIds.get("ADMIN"), 1.0, false, true, true, 0L));
        userRepository.save(new User(
                null, systemAdmin.id(), SYSTEM_ADMIN_EMAIL, initialHash, null, NotifPrefs.allOn(), 0L));

        log.info("시드 적재 완료 — 인원 {}(+시스템 1)·직급 {}·권한 그룹 4·조직 노드 {}",
                rows.size(), gradeIds.size(), orgUnitIds.count());
    }

    private List<SeedPersonRow> readRows(Path peopleJson) {
        try {
            return objectMapper.readValue(
                    Files.readAllBytes(peopleJson), new TypeReference<List<SeedPersonRow>>() {
                    });
        } catch (IOException | JacksonException e) { // Jackson 3는 언체크 JacksonException
            throw new IllegalStateException(
                    "시드 인력 파일을 읽을 수 없습니다: " + peopleJson.toAbsolutePath(), e);
        }
    }

    /** 직급 9종 — JSON의 (grade, gradeCoeff) 그대로, 계수 내림차순으로 적재 (부록 B 표와 동치). */
    private Map<String, Long> loadGrades(List<SeedPersonRow> rows) {
        Map<String, Double> coeffs = new LinkedHashMap<>();
        for (SeedPersonRow row : rows) {
            Double previous = coeffs.put(row.grade(), row.gradeCoeff());
            if (previous != null && previous != row.gradeCoeff()) {
                throw new IllegalStateException("시드 직급 계수 충돌: " + row.grade());
            }
        }
        Map<String, Long> ids = new HashMap<>();
        coeffs.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> ids.put(entry.getKey(),
                        gradeRepository.save(new Grade(null, entry.getKey(), entry.getValue(), 0L)).id()));

        return ids;
    }

    /** 조직 노드 id 인덱스 — 시드 한 행의 소속 노드를 해석한다. */
    private record OrgUnitIds(Long rootId, Map<String, Long> divisions, Map<String, Long> teams) {
        Long of(SeedPersonRow row) {
            if (row.team().equals(row.division())) {
                return divisions.get(row.division());
            }
            return teams.get(row.division() + "/" + row.team());
        }

        int count() {
            // divisions에는 root("프로텐")도 들어 있어 root 별도 가산 없음
            return divisions.size() + teams.size();
        }
    }

    /**
     * 회사(root)→부문→팀 2단 적재 (부록 B — 임의 깊이는 운영 중 US-E3로 확장).
     * 시드의 division "프로텐"(대표 직속)은 별도 부문 노드가 아니라 root 자신 —
     * root 직속 인원의 가시성 처리는 M1b 판단 ②와 정합.
     */
    private OrgUnitIds loadOrgTree(List<SeedPersonRow> rows) {
        Long rootId = orgUnitRepository.save(new OrgUnit(null, null, COMPANY_NAME, 0L)).id();
        Map<String, Long> divisions = new LinkedHashMap<>();
        divisions.put(COMPANY_NAME, rootId);
        Map<String, Long> teams = new LinkedHashMap<>();
        for (SeedPersonRow row : rows) {
            Long divisionId = divisions.computeIfAbsent(row.division(),
                    name -> orgUnitRepository.save(new OrgUnit(null, rootId, name, 0L)).id());
            if (!row.team().equals(row.division())) {
                teams.computeIfAbsent(row.division() + "/" + row.team(),
                        key -> orgUnitRepository.save(new OrgUnit(null, divisionId, row.team(), 0L)).id());
            }
        }

        return new OrgUnitIds(rootId, divisions, teams);
    }

    /** 기본 권한 그룹 4종 (2026-08-09 ⑦ — 상위 PRD §4-3) — 시드 orgRole 값으로 키를 잡는다. */
    private Map<String, Long> loadDefaultGroups() {
        Map<String, Long> ids = new HashMap<>();
        ids.put("ADMIN", permissionGroupRepository.save(new PermissionGroup(
                null, "관리자", VisibilityScope.COMPANY, true, true, true, true, true, 0L)).id());
        ids.put("DIVISION_HEAD", permissionGroupRepository.save(new PermissionGroup(
                null, "부문장", VisibilityScope.DIVISION, true, true, false, false, false, 0L)).id());
        ids.put("TEAM_LEAD", permissionGroupRepository.save(new PermissionGroup(
                null, "팀장", VisibilityScope.TEAM, true, true, false, false, false, 0L)).id());
        ids.put("MEMBER", permissionGroupRepository.save(new PermissionGroup(
                null, "팀원", VisibilityScope.SELF, false, false, false, false, false, 0L)).id());

        return ids;
    }
}
