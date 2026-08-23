package kr.proten.pms.resource.service.impl;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kr.proten.pms.person.OrgVisibility;
import kr.proten.pms.person.OrgVisibilityService;
import kr.proten.pms.person.WorkforceDirectoryService;
import kr.proten.pms.person.WorkforceProfile;
import kr.proten.pms.resource.service.dto.UtilizationQuery;
import org.springframework.stereotype.Component;

/**
 * 가동률 모집단 판정 — "누구를 셀 것인가" 하나만 답한다 (AC C1-1 범위 · C1-5).
 *
 * <p>산식과 떼어 낸 이유: 규칙이 두 축으로 갈리는데(개인 지정 vs 집계 · 전사 scope vs
 * 제한 scope) 그것을 산식과 한 클래스에 두면 "이 필터가 분자 얘기인가 모집단 얘기인가"를
 * 읽는 사람이 매번 되짚는다. {@code ProjectVisibilityService}와 같은 자리다.
 *
 * <p><b>가시성은 여기서 한 번만 묻는다</b>: 두 곳에서 물으면 같은 화자에게 두 판정이
 * 생기고, 그 사이에 권한이 바뀌면 목록과 단건이 어긋난다.
 */
@Component
class UtilizationPopulation {
    private final OrgVisibilityService orgVisibilityService;
    private final WorkforceDirectoryService workforceDirectory;

    UtilizationPopulation(
            OrgVisibilityService orgVisibilityService,
            WorkforceDirectoryService workforceDirectory) {
        this.orgVisibilityService = orgVisibilityService;
        this.workforceDirectory = workforceDirectory;
    }

    /**
     * 이 조회가 셀 인원의 속성 — 빈 목록이면 셀 사람이 없다.
     *
     * <p>404 문구를 여기서 정하지 않는다: 개인 지정의 빈 결과가 404이고 집계의 빈 결과는
     * 빈 목록이라, 같은 빈 값에 다른 답을 붙이는 판단은 유스케이스의 것이다.
     */
    List<WorkforceProfile> resolve(long callerPersonId, UtilizationQuery query) {
        OrgVisibility visibility = orgVisibilityService.visibilityOf(callerPersonId);

        if (query.isSinglePerson()) {
            if (!visibility.canView(query.personId())) {
                // 가시성에서 걸렸으면 인원 조회까지 가지 않는다 — 존재 여부가 새지 않는다.
                return List.of();
            }

            // C1-5는 집계 규칙이다 — 자기(또는 보이는 사람) 가동률은 billable과 무관하다.
            return workforceDirectory.findProfiles(Set.of(query.personId()));
        }

        Set<Long> scope = aggregateScope(visibility, query);

        if (scope.isEmpty()) {
            return List.of();
        }

        return workforceDirectory.findProfiles(scope).stream()
                .filter(WorkforceProfile::billable)
                .toList();
    }

    /**
     * 집계 대상 인원 id — 가시성과 요청 범위의 교집합이다.
     *
     * <p>{@code unrestricted}일 때 {@link OrgVisibility#visiblePersonIds()}가 <b>빈
     * 집합</b>인 것이 이 메서드가 있는 이유다: "제약이 없다"는 뜻이지 "아무도 없다"가
     * 아니므로 그대로 쓰면 전사 관리자에게 아무도 보이지 않는다.
     */
    private Set<Long> aggregateScope(OrgVisibility visibility, UtilizationQuery query) {
        if (query.orgUnitId() == null) {
            return visibility.unrestricted()
                    ? workforceDirectory.findAllAggregatablePersonIds()
                    : visibility.visiblePersonIds();
        }

        // ASSUMPTION: 없는 조직이나 가시성 밖 조직을 물으면 빈 목록이 된다(404가 아니다).
        // 조직 자체의 가시성을 물으려면 person이 계약을 하나 더 열어야 하는데, §7에 그
        // 오류 규칙이 없어 명세 없는 이유로 경계를 넓히지 않는다 — PRD-pms §12 등재.
        Set<Long> subtree = workforceDirectory.findPersonIdsInSubtree(query.orgUnitId());

        if (visibility.unrestricted()) {
            return subtree;
        }

        return subtree.stream()
                .filter(visibility::canView)
                .collect(Collectors.toUnmodifiableSet());
    }
}
