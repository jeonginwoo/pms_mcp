package kr.proten.pmsmock.mock;

import kr.proten.pmsmock.MockData;
import kr.proten.pmsmock.model.Person;
import kr.proten.pmsmock.model.Project;
import kr.proten.pmsmock.model.VisibilityScope;

/**
 * 가시성 판정 (상위 PRD §4·§4-4) — 그룹 scope 4단 + 프로젝트 참여 확장.
 * 참여 확장은 프로젝트 컨텍스트 내로 한정: 프로젝트는 참여하면 보이지만,
 * 타 팀 인원의 프로젝트 밖 데이터(인력·가동률)는 조직 가시성 규칙 유지.
 */
public class VisibilityPolicy {

    private final MockData data;

    public VisibilityPolicy(MockData data) {
        this.data = data;
    }

    public boolean canSeeProject(Person caller, Project project) {
        if (project.isParticipant(caller.id())) {
            return true;
        }
        return switch (data.groupOf(caller).scope()) {
            case COMPANY -> true;
            case DIVISION -> project.division().equals(caller.division());
            case TEAM -> project.team().equals(caller.team());
            case SELF -> false;
        };
    }

    public boolean canSeePerson(Person caller, Person target) {
        return switch (data.groupOf(caller).scope()) {
            case COMPANY -> true;
            case DIVISION -> target.division().equals(caller.division());
            case TEAM -> target.team().equals(caller.team());
            case SELF -> target.id() == caller.id();
        };
    }

    /** 집계 scope 허용 여부 — MY_TEAM은 팀 가시성 이상, DIVISION은 부문 가시성 이상 */
    public boolean canAggregate(Person caller, String scope) {
        VisibilityScope vs = data.groupOf(caller).scope();
        return switch (scope) {
            case "MY_TEAM" -> vs == VisibilityScope.COMPANY || vs == VisibilityScope.DIVISION
                    || vs == VisibilityScope.TEAM;
            case "DIVISION" -> vs == VisibilityScope.COMPANY || vs == VisibilityScope.DIVISION;
            default -> true;
        };
    }
}
