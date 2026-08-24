package kr.proten.pms.project.seed;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.entity.Engagement;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.service.entity.ProjectKey;
import kr.proten.pms.project.service.entity.ProjectRole;
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
 * 프로젝트·배정 시드 적재 (부록 B — `projects.json` 382건).
 *
 * <p>인원 시드와 달리 SQL이 아니라 <b>엔티티로 적재한다</b>: 원본이 JSON이고 id가
 * 없으므로 옮겨 적을 식별자가 없고, 무엇보다 상태·진척률·배정을 만드는 규칙이
 * 이미 엔티티에 있다. SQL로 밀어 넣으면 그 규칙을 우회한 데이터가 생겨,
 * "앱이 만들 수 없는 상태"가 시드에만 존재하게 된다.
 *
 * <p><b>상태는 상태 머신을 실제로 통과시켜 만든다</b>(§5): 계약대기 → 수주확정 →
 * 진행중 → (진척률 100) → 완료. `Project.create()`가 항상 계약대기·0에서
 * 시작하는 것은 규칙이지 제약이 아니므로, 시드도 그 길로 간다. 382건을 이 길로
 * 통과시키는 것 자체가 상태 머신의 통합 검증이다.
 *
 * <p>시드 원본과 도메인이 어긋나는 두 곳은 <b>적재 시 보정</b>하고 원본 JSON은
 * 수정하지 않는다(부록 B — OFFSITE 결정과 같은 형태. 시트를 다시 내려받아도 규칙이
 * 살아남는다):
 * <ul>
 *   <li>{@code engagement=OFFSITE} 32건 → {@code REMOTE} (2026-08-09 ③⑥ OFFSITE 폐지)
 *   <li>{@code status=완료}인데 {@code progress<100}인 13건 → 100 (2026-08-23 결정).
 *       시트에서 상태 칸만 완료로 바꾸고 진척률 칸을 갱신하지 않은 자국이고,
 *       완료의 전제가 100%다(AC A7-2). 상태를 진행중으로 내리는 쪽은 부록 B가
 *       기대값으로 고정한 "완료 319 · 진행중 34"를 깨뜨리므로 택하지 않았다
 * </ul>
 *
 * <p>멱등은 <b>"프로젝트 테이블이 비었나"</b>로 판정한다 — 이름 키로 판정하면
 * 시드에 이름이 같은 서로 다른 프로젝트가 있어(사이버다임 "TCK 검색엔진 추가"
 * 2건 — 기간·PM·solution이 다르다) 382건이 381건으로 접힌다.
 */
@Component
@Order(ProjectSeedLoader.ORDER)
class ProjectSeedLoader implements ApplicationRunner {
    /** 인원 시드 뒤에 돈다 — managerId·assigneeIds가 실재하는지 검사해야 한다. */
    static final int ORDER = 100;

    private static final String SEED_FILE = "projects.json";
    private static final double DAYS_PER_MONTH = 30.4;

    private static final Logger log = LoggerFactory.getLogger(ProjectSeedLoader.class);

    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final PersonDirectoryService personDirectoryService;
    private final ObjectMapper objectMapper;
    // 시드 디렉터리 — 빈 값이면 적재하지 않는다 (테스트·이미 적재된 환경)
    private final String seedPath;

    ProjectSeedLoader(
            ProjectRepository projectRepository,
            ProjectAssignmentRepository assignmentRepository,
            PersonDirectoryService personDirectoryService,
            ObjectMapper objectMapper,
            @Value("${pms.seed.path:}") String seedPath) {
        this.projectRepository = projectRepository;
        this.assignmentRepository = assignmentRepository;
        this.personDirectoryService = personDirectoryService;
        this.objectMapper = objectMapper;
        this.seedPath = seedPath;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seedPath.isBlank()) {
            log.info("프로젝트 시드 적재 비활성 — pms.seed.path 미설정");

            return;
        }

        if (projectRepository.count() > 0) {
            log.info("프로젝트 시드 적재 생략 — 이미 {}건 있다", projectRepository.count());

            return;
        }

        Path seedFile = Path.of(seedPath, SEED_FILE);

        if (!Files.isReadable(seedFile)) {
            log.warn("프로젝트 시드 파일을 읽을 수 없다 — {}", seedFile.toAbsolutePath());

            return;
        }

        load(seedFile);
    }

    private void load(Path seedFile) {
        List<SeedProject> records = read(seedFile);
        int offsiteFixed = 0;
        int progressFixed = 0;
        int assignmentCount = 0;

        for (SeedProject record : records) {
            requireKnownPeople(record);

            if (record.isOffsite()) {
                offsiteFixed++;
            }

            if (record.needsProgressFix()) {
                progressFixed++;
            }

            Project saved = projectRepository.save(toProject(record));
            assignmentCount += assignmentRepository.saveAll(toAssignments(record, saved)).size();
        }

        log.info("프로젝트 시드 적재 완료 — 프로젝트 {}건(배정 {}행). 보정: OFFSITE→REMOTE {}건 ·"
                        + " 완료인데 진척률<100 → 100 {}건",
                records.size(), assignmentCount, offsiteFixed, progressFixed);
    }

    private List<SeedProject> read(Path seedFile) {
        try {
            return List.of(objectMapper.readValue(seedFile.toFile(), SeedProject[].class));
        } catch (RuntimeException cause) {
            // Jackson 3의 JacksonException은 언체크다 — 원인을 감추지 않고 기동을 세운다
            throw new IllegalStateException("프로젝트 시드를 읽을 수 없다: " + seedFile, cause);
        }
    }

    /**
     * 목표 상태까지 §5 전이를 실제로 밟는다. 유지보수중은 이관(US-D1)의 결과라
     * 이 길에 없고, 시드에도 그 상태의 레코드가 없다.
     */
    private Project toProject(SeedProject record) {
        Project project = Project.create(
                new ProjectKey(record.client(), record.name()),
                record.solution(),
                record.toEngagement(),
                record.managerId(),
                record.contractMm(),
                record.startDate(),
                record.endDate());
        ProjectStatus target = record.projectStatus();

        if (target == ProjectStatus.CONTRACT_PENDING) {
            return project;
        }

        project.advanceStatusTo(ProjectStatus.ORDER_CONFIRMED);

        if (target == ProjectStatus.ORDER_CONFIRMED) {
            return project;
        }

        project.advanceStatusTo(ProjectStatus.IN_PROGRESS);
        // 시각은 남기지 않는다 — 적재 시점은 도달 시점이 아니다(F3-1 주석)
        project.updateProgress(record.effectiveProgress(), null);

        if (target == ProjectStatus.COMPLETED) {
            project.complete();
        }

        return project;
    }

    /**
     * 배정 M/M 부여 규칙 (부록 B, 2026-08-10 확정).
     * 실무자 = PM 외 참여자, 없으면 PM 본인. 실무자가 따로 있으면 PM 배정은 0이다
     * (체크 역할은 부하가 아니다 — A6-7 기본값과 같은 취급). 상한은 두지 않는다:
     * 합이 100%를 넘는 달이 자연 발생해야 오버부킹 시연이 성립한다.
     */
    private List<ProjectAssignment> toAssignments(SeedProject record, Project project) {
        Set<Long> workers = record.workers();
        double share = round2(record.contractMm() / record.months() / workers.size());
        List<ProjectAssignment> assignments = new ArrayList<>();

        assignments.add(ProjectAssignment.of(
                project.getId(),
                record.managerId(),
                ProjectRole.PM,
                project.getStartDate(),
                project.getEndDate(),
                workers.contains(record.managerId()) ? share : 0));

        for (Long personId : record.participants()) {
            assignments.add(ProjectAssignment.of(
                    project.getId(),
                    personId,
                    ProjectRole.PARTICIPANT,
                    project.getStartDate(),
                    project.getEndDate(),
                    share));
        }

        return assignments;
    }

    /**
     * managerId·assigneeIds가 실재하는 인원인지 확인한다. 없는 id로 배정을 만들면
     * 조회 시점에 이름이 null로 새고 가동률 모집단에 유령이 낀다 — 적재 직후에
     * 실패시켜 원인을 드러낸다(인원 시드와 같은 원칙).
     */
    private void requireKnownPeople(SeedProject record) {
        Set<Long> referenced = new LinkedHashSet<>(record.assigneeIds());
        referenced.add(record.managerId());

        for (Long personId : referenced) {
            if (!personDirectoryService.existsActive(personId)) {
                throw new IllegalStateException(
                        "프로젝트 시드가 없는 인원을 가리킨다 — %s / %s, personId=%d"
                                .formatted(record.client(), record.name(), personId));
            }
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /**
     * `projects.json` 한 레코드. `team`·`division`은 시트 출처 메타라 받지 않는다 —
     * 프로젝트의 조직 귀속은 배정 인원이 정하고(상위 PRD §4-4) 엔티티에 그 컬럼이 없다.
     */
    record SeedProject(
            String name,
            String client,
            String status,
            int progress,
            LocalDate startDate,
            LocalDate endDate,
            double contractMm,
            String engagement,
            String solution,
            long managerId,
            List<Long> assigneeIds) {

        ProjectStatus projectStatus() {
            for (ProjectStatus candidate : ProjectStatus.values()) {
                if (candidate.label().equals(status)) {
                    return candidate;
                }
            }

            throw new IllegalStateException("시드의 알 수 없는 프로젝트 상태: " + status);
        }

        boolean isOffsite() {
            return "OFFSITE".equals(engagement);
        }

        Engagement toEngagement() {
            // OFFSITE 폐지 — 비상주는 원격으로 흡수한다 (부록 B)
            return isOffsite() ? Engagement.REMOTE : Engagement.valueOf(engagement);
        }

        boolean needsProgressFix() {
            return projectStatus() == ProjectStatus.COMPLETED && progress != 100;
        }

        int effectiveProgress() {
            return needsProgressFix() ? 100 : progress;
        }

        /** 프로젝트 개월수 — 최소 1개월. 하루짜리 프로젝트도 그 달에는 투입이 있다. */
        long months() {
            if (startDate == null || endDate == null) {
                return 1;
            }

            long days = ChronoUnit.DAYS.between(startDate, endDate);

            return Math.max(1, Math.round(days / DAYS_PER_MONTH));
        }

        /** PM 외 참여자 — 아무도 없으면 PM 본인이 실무자다. */
        Set<Long> workers() {
            Set<Long> workers = participants();

            return workers.isEmpty() ? Set.of(managerId) : workers;
        }

        /** PM을 뺀 배정 인원. PM은 항상 role=PM 한 행으로 따로 만든다(A6-5 불변식). */
        Set<Long> participants() {
            Set<Long> participants = new LinkedHashSet<>(assigneeIds());
            participants.remove(managerId);

            return participants;
        }

        @Override
        public List<Long> assigneeIds() {
            return assigneeIds == null ? List.of() : assigneeIds;
        }
    }
}
