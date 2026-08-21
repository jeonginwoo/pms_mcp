package kr.proten.pms.project.service.impl;

import kr.proten.pms.project.service.dto.ProjectSummary;
import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.AssignmentView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.proten.pms.person.service.PersonDirectoryService;
import kr.proten.pms.person.service.dto.PersonRef;
import kr.proten.pms.project.service.entity.AssignmentStatus;
import kr.proten.pms.project.service.entity.Project;
import kr.proten.pms.project.service.entity.ProjectAssignment;
import kr.proten.pms.project.repository.ProjectAssignmentRepository;
import org.springframework.stereotype.Component;

/**
 * 프로젝트 엔티티 → 응답 표현 변환.
 * 엔티티를 계층 밖으로 내보내지 않으려는 규칙(conventions/java-spring.md §4)의
 * 경계이며, 인원 이름은 person 모듈의 참조 포트로 한 번에 채운다(개별 조회 반복 금지).
 */
@Component
class ProjectViewFactory {
    private final ProjectAssignmentRepository assignmentRepository;
    private final PersonDirectoryService personDirectoryService;

    ProjectViewFactory(
            ProjectAssignmentRepository assignmentRepository,
            PersonDirectoryService personDirectoryService) {
        this.assignmentRepository = assignmentRepository;
        this.personDirectoryService = personDirectoryService;
    }

    List<ProjectSummary> toSummaries(List<Project> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }

        Map<Long, String> managerNames = personNames(projects.stream()
                .map(Project::getManagerId)
                .collect(Collectors.toUnmodifiableSet()));

        return projects.stream()
                .map(project -> new ProjectSummary(
                        project.getId(),
                        project.getClient(),
                        project.getName(),
                        project.getStatus(),
                        project.getProgress(),
                        project.getManagerId(),
                        managerNames.get(project.getManagerId())))
                .toList();
    }

    /** 배정 레코드를 함께 실어 상세를 만든다 — 진행 중 배정만 노출한다. */
    ProjectDetail toDetail(Project project) {
        return toDetail(project, assignmentRepository.findByProjectIdAndStatus(
                project.getId(), AssignmentStatus.ACTIVE));
    }

    ProjectDetail toDetail(Project project, List<ProjectAssignment> assignments) {
        return new ProjectDetail(
                project.getId(),
                project.getClient(),
                project.getName(),
                project.getSolution(),
                project.getEngagement(),
                project.getStatus(),
                project.getPhase(),
                project.getProgress(),
                project.getContractMm(),
                project.getStartDate(),
                project.getEndDate(),
                project.getManagerId(),
                project.getVersion(),
                toAssignmentViews(assignments));
    }

    /** 배정 한 건의 표현 (AC B1-1·B1-4 응답) — 이름 해석 규칙을 목록과 공유한다. */
    AssignmentView toView(ProjectAssignment assignment) {
        return toAssignmentViews(List.of(assignment)).getFirst();
    }

    private List<AssignmentView> toAssignmentViews(List<ProjectAssignment> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }

        Map<Long, String> personNames = personNames(assignments.stream()
                .map(ProjectAssignment::getPersonId)
                .collect(Collectors.toUnmodifiableSet()));

        return assignments.stream()
                .map(assignment -> new AssignmentView(
                        assignment.getId(),
                        assignment.getPersonId(),
                        personNames.get(assignment.getPersonId()),
                        assignment.getRole(),
                        assignment.getStartDate(),
                        assignment.getEndDate(),
                        assignment.getMonthlyMm(),
                        assignment.getVersion()))
                .toList();
    }

    private Map<Long, String> personNames(Set<Long> personIds) {
        return personDirectoryService.findRefs(personIds).stream()
                .collect(Collectors.toMap(PersonRef::id, PersonRef::name));
    }
}
