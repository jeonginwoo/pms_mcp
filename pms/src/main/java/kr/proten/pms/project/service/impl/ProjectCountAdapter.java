package kr.proten.pms.project.service.impl;

import java.util.Map;
import java.util.stream.Collectors;
import kr.proten.pms.person.ProjectCountPort;
import kr.proten.pms.project.repository.ProjectRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * person이 정의한 {@link ProjectCountPort}를 project가 구현한다 (2026-08-26).
 *
 * <p>{@code AssignmentCountAdapter}와 같은 자리·같은 이유다 — 계약은 person의 것이고
 * project는 그것을 채울 뿐이라 밖으로 열 면이 없어 {@code service/impl}에 둔다.
 *
 * <p>세는 것은 <b>대표 PM({@code managerId})</b>이지 배정이 아니다. §12의 정의가 그렇고,
 * 배정으로 세면 한 프로젝트가 여러 노드에 동시에 걸려 합이 382를 넘는다.
 */
@Component
@Transactional(readOnly = true)
class ProjectCountAdapter implements ProjectCountPort {
    private final ProjectRepository projectRepository;

    ProjectCountAdapter(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public Map<Long, Long> countByManager() {
        return projectRepository.countByManager().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }
}
