package kr.proten.pms.project.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import kr.proten.pms.person.AssignmentReleasePort;
import kr.proten.pms.person.LiveAssignment;
import kr.proten.pms.project.AssignmentChanged;
import kr.proten.pms.project.ProjectStatus;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * person이 정의한 {@link AssignmentReleasePort}를 project가 구현한다 (2026-08-26).
 *
 * <p>{@code AssignmentCountAdapter}·{@code ProjectCountAdapter}와 같은 자리·같은 이유다 —
 * 계약은 person의 것이고 project는 그것을 채울 뿐이라 밖으로 열 면이 없어
 * {@code service/impl}에 둔다.
 *
 * <p><b>{@code AssignmentService.close}(B2-1)를 재사용하지 않는다</b> — D1 이관 어댑터가
 * {@code ContractCommandService}를 재사용하지 않은 것과 같은 판단이다(2026-08-25 선례).
 * 그 경로는 <b>프로젝트별 {@code ASSIGN} 권한(=PM)</b>을 요구하는데, 퇴사 처리를 하는
 * 사람은 "사용자/조직 관리" 권한자이지 그 사람이 물린 프로젝트들의 PM이 아니다.
 * 그 관문을 지나게 하면 <b>관리자가 남의 프로젝트 PM이 아니라는 이유로 퇴사 처리를
 * 못 한다</b>. 관문은 이미 호출부(E2-3)가 자기 것으로 들었다.
 *
 * <p>대신 <b>종료의 규칙 자체는 그대로 쓴다</b>: {@code close()}가 endDate를 종료월
 * 말일로 당기고(AC B2-1), 감사 행과 {@code AssignmentChanged}도 같은 모양으로 남는다 —
 * 퇴사로 끊긴 배정이 이력에서 손으로 끊은 것과 달라 보일 이유가 없다.
 */
@Component
@Transactional
class AssignmentReleaseAdapter implements AssignmentReleasePort {
    private final ProjectAssignmentRepository assignmentRepository;
    private final ProjectAuditRecorder projectAuditRecorder;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    AssignmentReleaseAdapter(
            ProjectAssignmentRepository assignmentRepository,
            ProjectAuditRecorder projectAuditRecorder,
            Clock clock,
            ApplicationEventPublisher events) {
        this.assignmentRepository = assignmentRepository;
        this.projectAuditRecorder = projectAuditRecorder;
        this.clock = clock;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiveAssignment> findLiveAssignments(long personId) {
        return assignmentRepository.findLiveByPerson(
                personId, AssignmentStatus.ACTIVE, ProjectStatus.live());
    }

    @Override
    public int closeParticipantAssignments(long callerPersonId, long personId) {
        List<ProjectAssignment> live = assignmentRepository.findLiveEntitiesByPerson(
                personId, AssignmentStatus.ACTIVE, ProjectStatus.live());
        LocalDate today = LocalDate.now(clock);
        int closed = 0;

        for (ProjectAssignment assignment : live) {
            // PM은 여기 오지 않는다(호출부가 먼저 거절한다) — 그래도 거르는 이유는
            // 이 계약 하나만 보고도 A6-5 불변식이 성립하게 하려는 것이다
            if (assignment.isManager()) {
                continue;
            }

            Map<String, Object> before = projectAuditRecorder.snapshot(assignment);
            assignment.close(today);
            ProjectAssignment saved = assignmentRepository.saveAndFlush(assignment);
            projectAuditRecorder.assignmentClosed(callerPersonId, saved, before);
            events.publishEvent(new AssignmentChanged(
                    AssignmentChanged.Kind.CLOSED,
                    saved.getProjectId(),
                    null,
                    saved.getPersonId(),
                    AssignmentChanged.monthsOf(
                            saved.getStartDate(), saved.getEndDate(), YearMonth.now(clock))));
            closed++;
        }

        return closed;
    }
}
