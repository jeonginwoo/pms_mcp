package kr.proten.pms.person.seed;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import kr.proten.pms.person.AccountPort;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 조직·직급·권한 그룹·인원 시드 적재 (부록 B).
 *
 * 기동 시 시드 섹션 중 하나라도 비어 있으면 `seed_org_proten.sql`을 실행한다. 스키마는
 * Flyway가 소유하지만 이 참조 데이터는 마이그레이션으로 두지 않았다 — 테스트가
 * 자체 픽스처로 같은 테이블을 쓰기 때문에, 마이그레이션이면 모든 테스트에
 * 43명이 함께 적재돼 "팀 범위가 정확히 N명" 같은 단정이 무너진다.
 * `pms.seed.path`가 비어 있으면 비활성이고, 테스트 프로필은 이를 설정하지 않는다.
 *
 * ASSUMPTION: 엔티티가 아니라 SQL 스크립트로 적재한다 — 시드 원본이 SQL이고
 * 식별자가 원본에 고정돼 있어(부록 B의 id 정합 전제) 변환 계층을 두면 원본과
 * 코드 두 곳에 같은 표가 생긴다. 대신 적재 후 참조 정합성을 검사해 조용한
 * 오연결을 막는다.
 */
@Component
// 프로젝트 시드(ProjectSeedLoader)가 이 뒤에 돈다 — 배정이 인원을 참조한다
@Order(0)
class PersonSeedLoader implements ApplicationRunner {
    private static final String SEED_FILE = "seed_org_proten.sql";

    private static final Logger log = LoggerFactory.getLogger(PersonSeedLoader.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PersonRepository personRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final GradeRepository gradeRepository;
    private final PermissionGroupRepository permissionGroupRepository;
    private final AccountPort accountPort;
    // 시드 디렉터리 — 빈 값이면 적재하지 않는다 (테스트·이미 적재된 환경)
    private final String seedPath;

    PersonSeedLoader(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            PersonRepository personRepository,
            OrgUnitRepository orgUnitRepository,
            GradeRepository gradeRepository,
            PermissionGroupRepository permissionGroupRepository,
            AccountPort accountPort,
            @Value("${pms.seed.path:}") String seedPath) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.personRepository = personRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.gradeRepository = gradeRepository;
        this.permissionGroupRepository = permissionGroupRepository;
        this.accountPort = accountPort;
        this.seedPath = seedPath;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedPath.isBlank()) {
            log.info("시드 적재 비활성 — pms.seed.path 미설정");

            return;
        }

        String missing = missingSection();

        if (missing == null) {
            log.info("시드 적재 생략 — 참조 데이터가 이미 있다 (인원 {}명)",
                    personRepository.count());

            return;
        }

        log.info("시드 적재 시작 — {}이(가) 비어 있다", missing);
        Path seedFile = Path.of(seedPath, SEED_FILE);

        if (!Files.isReadable(seedFile)) {
            throw new IllegalStateException("시드 파일을 읽을 수 없습니다: " + seedFile);
        }

        load(seedFile);
        alignReferenceIdSequences();
        verifyReferences();
        log.info("시드 적재 완료 — 조직 {} · 직급 {} · 권한 그룹 {} · 인원 {} · 계정 {}",
                orgUnitRepository.count(),
                gradeRepository.count(),
                permissionGroupRepository.count(),
                personRepository.count(),
                accountPort.count());
    }

    /**
     * 비어 있는 시드 섹션의 이름 — 전부 채워져 있으면 null.
     *
     * 인원만 보지 않는 이유: 시드에 섹션이 추가되면(계정이 그랬다) 이미 인원이 있는 DB는
     * 영원히 그 섹션을 받지 못한다. 스크립트가 전부 ON CONFLICT DO NOTHING이라
     * 다시 실행해도 안전하므로, 하나라도 비면 전체를 다시 흘린다.
     * 섹션이 늘면 여기에 한 줄 추가한다.
     */
    private String missingSection() {
        if (personRepository.count() == 0) {
            return "인원";
        }

        if (accountPort.count() == 0) {
            return "로그인 계정";
        }

        return null;
    }

    /**
     * 시드가 명시 id로 넣은 참조 데이터 3종의 시퀀스를 그 최대값에 맞춘다
     * (조직 2026-08-22 · 직급·권한 그룹 2026-08-24).
     * 하지 않으면 빈 DB에 시드를 적재한 뒤 첫 등록(E3-1·E4-1·E5-1)이 시드 id와 충돌한다.
     */
    private void alignReferenceIdSequences() {
        alignOrgUnitIdSequence();

        // 직급·권한 그룹도 명시 id로 들어왔다 — 맞추지 않으면 첫 등록(E4-1·E5-1)이
        // 시드 id와 충돌한다. 기준은 V11과 같은 역대 최고값이다.
        jdbcTemplate.execute("""
                select setval('grade_id_seq', greatest(
                        (select coalesce(max(id), 1) from grades),
                        (select coalesce(max(grade_id), 1) from people),
                        (select coalesce(max(entity_id), 1) from audit_logs
                          where entity_type = 'Grade')))""");
        jdbcTemplate.execute("""
                select setval('permission_group_id_seq', greatest(
                        (select coalesce(max(id), 1) from permission_groups),
                        (select coalesce(max(group_id), 1) from people),
                        (select coalesce(max(entity_id), 1) from audit_logs
                          where entity_type = 'PermissionGroup')))""");
    }

    private void alignOrgUnitIdSequence() {
        // 마이그레이션 V6와 같은 기준(역대 최고값)을 쓴다 — 살아 있는 노드·인원이
        // 가리키는 노드·감사 로그의 노드 id 중 가장 큰 값
        jdbcTemplate.execute("""
                select setval('org_unit_id_seq', greatest(
                        (select coalesce(max(id), 1) from org_units),
                        (select coalesce(max(org_unit_id), 1) from people),
                        (select coalesce(max(entity_id), 1) from audit_logs
                          where entity_type = 'OrgUnit')))""");
    }

    private void load(Path seedFile) {
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new FileSystemResource(seedFile));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
    }

    /**
     * 인원이 가리키는 조직·직급·권한 그룹이 실제로 있는지 확인한다.
     * 참조가 비면 조회 시점에 조직명·직급명이 null로 새거나 권한 그룹 부재로
     * 기동 후 첫 요청이 터진다 — 적재 직후에 실패시켜 원인을 드러낸다.
     */
    private void verifyReferences() {
        for (Person person : personRepository.findAll()) {
            requireExists("조직", person, orgUnitRepository.existsById(person.getOrgUnitId()),
                    person.getOrgUnitId());
            requireExists("직급", person, gradeRepository.existsById(person.getGradeId()),
                    person.getGradeId());
            requireExists("권한 그룹", person,
                    permissionGroupRepository.existsById(person.getGroupId()), person.getGroupId());
        }
    }

    private void requireExists(String label, Person person, boolean exists, Long referencedId) {
        if (!exists) {
            throw new IllegalStateException(
                    "시드 참조 불일치 — 인원 %d(%s)의 %s id %d 없음"
                            .formatted(person.getId(), person.getName(), label, referencedId));
        }
    }
}
