package kr.proten.pms.person.service.impl;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.proten.pms.person.service.dto.OrgVisibility;
import kr.proten.pms.person.service.OrgVisibilityService;
import kr.proten.pms.person.service.entity.OrgTree;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직 가시성 판정 (상위 PRD §4-3·§4-4) — 그룹 scope를 가시 인원 id 집합으로 접는다.
 * scope별 해석은 {@link OrgScopeResolver} 구현들에 위임하므로 이 서비스는 조합과
 * 질의만 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class OrgVisibilityServiceImpl implements OrgVisibilityService {
    private final RequesterResolver requesterResolver;
    private final PersonRepository personRepository;
    private final OrgUnitRepository orgUnitRepository;
    // scope → 해석자 — 주입된 구현으로 채우므로 scope 추가 시 이 클래스는 그대로다
    private final Map<VisibilityScope, OrgScopeResolver> scopeResolvers;

    public OrgVisibilityServiceImpl(
            RequesterResolver requesterResolver,
            PersonRepository personRepository,
            OrgUnitRepository orgUnitRepository,
            List<OrgScopeResolver> scopeResolvers) {
        this.requesterResolver = requesterResolver;
        this.personRepository = personRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.scopeResolvers = scopeResolvers.stream().collect(Collectors.toMap(
                OrgScopeResolver::supports,
                resolver -> resolver,
                (first, second) -> first,
                () -> new EnumMap<>(VisibilityScope.class)));
    }

    @Override
    public OrgVisibility visibilityOf(long callerPersonId) {
        Requester requester = requesterResolver.resolve(callerPersonId);
        OrgScopeResolver scopeResolver = resolverFor(requester.group().getVisibilityScope());

        if (scopeResolver.unrestricted()) {
            return OrgVisibility.unrestricted(callerPersonId);
        }

        Set<Long> orgUnitIds = scopeResolver.visibleOrgUnitIds(requester.person(), orgTree());

        if (orgUnitIds.isEmpty()) {
            return OrgVisibility.of(callerPersonId, Set.of());
        }

        return OrgVisibility.of(callerPersonId, personIdsIn(orgUnitIds));
    }

    private OrgScopeResolver resolverFor(VisibilityScope scope) {
        OrgScopeResolver resolver = scopeResolvers.get(scope);

        if (resolver == null) {
            throw new IllegalStateException("가시성 scope 해석자 없음: " + scope);
        }

        return resolver;
    }

    private OrgTree orgTree() {
        return OrgTree.of(orgUnitRepository.findAll());
    }

    /** 조직 집합에 속한 활성 인원 id. id를 정렬해 넘겨 질의 파라미터를 결정적으로 유지한다. */
    private Set<Long> personIdsIn(Set<Long> orgUnitIds) {
        Collection<Long> sortedIds = orgUnitIds.stream().sorted().toList();

        return personRepository.findByOrgUnitIdInAndActiveTrue(sortedIds).stream()
                .map(Person::getId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
