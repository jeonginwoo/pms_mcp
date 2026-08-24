package kr.proten.pms.project.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;
import kr.proten.pms.project.ProjectReminderDue;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.repository.ProjectRepository;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.service.entity.ProjectRole;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마감 임박·완료 지연 일일 점검 (US-F2 · US-F3).
 *
 * <p><b>알림을 직접 만들지 않고 발행한다</b>: 이 프로젝트의 규칙은 "notification 밖에서
 * {@code notify}를 부르는 모듈은 없다"이고(2026-08-24 확정), 스케줄러라고 예외를 두면
 * 그 규칙이 규칙이 아니게 된다. 그래서 여기는 <b>대상을 찾는 일</b>만 하고 적재는
 * 구독자가 한다({@code ProjectReminderDue}).
 *
 * <p><b>project에 있는 이유</b>: "어느 프로젝트가 D-7인가"·"어느 프로젝트가 100%인 채
 * 멎었는가"는 프로젝트 판단이고 데이터도 project가 갖는다. notification에 두면 그
 * 판단이 넘어가고 project는 화자 없는 조회를 두 개 더 열어야 한다.
 *
 * <p><b>멱등은 구독자의 {@code dedupeKey}가 든다</b>(F2-2·F3-2). 여기서 "오늘 이미
 * 보냈나"를 기억하지 않는 이유는 그 기억이 곧 두 번째 저장소가 되기 때문이다 —
 * {@code notifications} 표의 유니크 제약이 이미 그 일을 한다(V7).
 *
 * <p>새벽에 도는 이유는 실무 시각이다: 출근해서 목록을 열면 그날 것이 이미 있어야 한다.
 * 시간대는 앱의 기본 시간대를 따른다({@code ClockConfig} — 전사 1개 지역이다).
 */
@Component
class ProjectReminderScheduler {
    /** AC F2-1 — 종료일 D-7 (N=7은 2026-08-06 확정). */
    private static final int DEADLINE_WINDOW_DAYS = 7;
    /** AC F3-1 — 100%인 채 7일 경과. */
    private static final int OVERDUE_DAYS = 7;

    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ProjectReminderScheduler(
            ProjectRepository projectRepository,
            ProjectAssignmentRepository assignmentRepository,
            ApplicationEventPublisher events,
            Clock clock) {
        this.projectRepository = projectRepository;
        this.assignmentRepository = assignmentRepository;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 매일 06:00 — 마감 임박(F2-1)과 완료 지연(F3-1)을 한 번에 훑는다.
     *
     * <p>둘을 한 메서드에 둔 이유는 <b>한 번의 기상</b>이기 때문이다. 나누면 크론이
     * 둘이 되고, 그 둘이 어긋날 때 "오늘 알림이 반만 왔다"가 된다.
     *
     * <p>{@code @Transactional}이 필요한 것은 발행 때문이다 —
     * {@code @ApplicationModuleListener}는 커밋 후에 돌므로 트랜잭션이 없으면
     * 구독자가 아예 깨어나지 않는다.
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    void sweep() {
        publishDeadlineNear();
        publishCompletionOverdue();
    }

    /** 종료일 D-7 이내인 진행중 프로젝트 → PM (F2-1). */
    private void publishDeadlineNear() {
        LocalDate today = LocalDate.now(clock);
        LocalDate through = today.plusDays(DEADLINE_WINDOW_DAYS);

        for (Project project : projectRepository.findDeadlineNear(today, through)) {
            events.publishEvent(new ProjectReminderDue(
                    ProjectReminderDue.Kind.DEADLINE_NEAR,
                    project.getId(),
                    project.getName(),
                    project.getEndDate(),
                    today,
                    // 마감은 PM 한 사람의 몫이다 — 배정 전원에게 보내면 소음이 된다
                    List.of(project.getManagerId())));
        }
    }

    /**
     * 100%인 채 7일 경과한 진행중 프로젝트 → PM·PL (F3-1).
     *
     * <p>참여자를 빼는 것은 AC가 적은 노이즈 방지이고, 개별 해제는 알림 설정(F1-5)이
     * 맡는다. PM이 배정 행에 없을 수도 있어({@code managerId}가 정본이다) 두 출처를
     * 합친다 — 그 합집합이 "이 프로젝트를 끌고 가는 사람들"이다.
     */
    private void publishCompletionOverdue() {
        Instant since = Instant.now(clock).minus(OVERDUE_DAYS, ChronoUnit.DAYS);

        for (Project project : projectRepository.findCompletionOverdue(since)) {
            events.publishEvent(new ProjectReminderDue(
                    ProjectReminderDue.Kind.COMPLETION_OVERDUE,
                    project.getId(),
                    project.getName(),
                    LocalDate.ofInstant(project.getHundredReachedAt(), clock.getZone()),
                    LocalDate.now(clock),
                    leadsOf(project)));
        }
    }

    /** PM·PL — {@code managerId}와 PL 배정의 합집합, 중복 없이. */
    private List<Long> leadsOf(Project project) {
        List<Long> pls = assignmentRepository
                .findByProjectIdAndRoleAndStatus(
                        project.getId(), ProjectRole.PL, AssignmentStatus.ACTIVE).stream()
                .map(ProjectAssignment::getPersonId)
                .toList();

        return Stream.concat(Stream.of(project.getManagerId()),
                        pls.stream())
                .distinct()
                .toList();
    }
}
