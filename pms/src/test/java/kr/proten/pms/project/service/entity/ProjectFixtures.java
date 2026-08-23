package kr.proten.pms.project.service.entity;

import kr.proten.pms.project.ProjectStatus;
import java.time.LocalDate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * project 모듈 테스트 픽스처.
 * 프로젝트·배정 식별자는 DB가 생성하므로(참조 데이터인 person과 달리 시드 id를
 * 보존할 필요가 없다) 단위 테스트에서만 식별자를 주입해 준다.
 */
public final class ProjectFixtures {
    public static final LocalDate START = LocalDate.of(2026, 8, 1);
    public static final LocalDate END = LocalDate.of(2026, 12, 31);

    private ProjectFixtures() {
    }

    /** 계약대기 상태의 신규 프로젝트 — 식별자 있음. */
    public static Project project(long id, String client, String name, long managerId) {
        Project project = Project.create(
                new ProjectKey(client, name),
                "검색엔진",
                Engagement.REMOTE,
                managerId,
                2.0,
                START,
                END);
        ReflectionTestUtils.setField(project, "id", id);

        return project;
    }

    /** 상태·진척률을 지정한 프로젝트 — 상태 전이 경로(범위 밖)를 우회해 만든다. */
    public static Project project(
            long id,
            String client,
            String name,
            long managerId,
            ProjectStatus status,
            int progress,
            long version) {
        Project project = project(id, client, name, managerId);
        ReflectionTestUtils.setField(project, "status", status);
        ReflectionTestUtils.setField(project, "progress", progress);
        ReflectionTestUtils.setField(project, "version", version);

        return project;
    }

    public static ProjectAssignment assignment(
            long id,
            long projectId,
            long personId,
            ProjectRole role) {
        ProjectAssignment assignment =
                ProjectAssignment.of(projectId, personId, role, START, END, 0.5);
        ReflectionTestUtils.setField(assignment, "id", id);

        return assignment;
    }

    /** version을 지정한 배정 — 낙관적 락 경로(AC B1-4) 검증용. */
    public static ProjectAssignment assignment(
            long id,
            long projectId,
            long personId,
            ProjectRole role,
            long version) {
        ProjectAssignment assignment = assignment(id, projectId, personId, role);
        ReflectionTestUtils.setField(assignment, "version", version);

        return assignment;
    }
}
