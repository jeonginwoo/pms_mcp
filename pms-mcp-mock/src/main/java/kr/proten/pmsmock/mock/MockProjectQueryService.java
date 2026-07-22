package kr.proten.pmsmock.mock;

import java.util.List;
import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.port.AssignmentDto;
import kr.proten.pmsmock.port.ProjectDto;
import kr.proten.pmsmock.port.ProjectQueryPort;
import org.springframework.stereotype.Component;

/**
 * 프로젝트 검색 인메모리 구현 — PMS 구현 후 폐기한다.
 */
@Component
public class MockProjectQueryService implements ProjectQueryPort {
    @Override
    public List<ProjectDto> searchVisible(String status, String keyword, Long projectId) {
        if (projectId != null) {
            return MockData.PROJECTS.stream()
                    .filter(p -> p.id() == projectId)
                    .map(p -> toDto(p, assignmentsOf(p.id())))
                    .toList();
        }

        return MockData.PROJECTS.stream()
                .filter(p -> status == null || p.status().equals(status))
                .filter(p -> keyword == null || p.name().contains(keyword) || p.client().contains(keyword))
                .map(p -> toDto(p, null))
                .toList();
    }

    /**
     * 프로젝트의 전체 배정을 담당자 이름과 함께 조회합니다.
     */
    private List<AssignmentDto> assignmentsOf(long projectId) {
        return MockData.ASSIGNMENTS.stream()
                .filter(a -> a.projectId() == projectId)
                .map(a -> new AssignmentDto(personName(a.personId()), a.month(), a.mm()))
                .toList();
    }

    private String personName(long personId) {
        return MockData.PEOPLE.stream()
                .filter(p -> p.id() == personId)
                .map(MockData.MockPerson::name)
                .findFirst()
                .orElse("(미상)");
    }

    private ProjectDto toDto(MockData.MockProject p, List<AssignmentDto> assignments) {
        return new ProjectDto(p.id(), p.name(), p.client(), p.status(), p.progress(),
                p.startDate(), p.endDate(), assignments);
    }
}
