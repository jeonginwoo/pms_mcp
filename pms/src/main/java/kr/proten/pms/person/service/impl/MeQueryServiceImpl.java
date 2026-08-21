package kr.proten.pms.person.service.impl;

import kr.proten.pms.person.service.MeQueryService;
import kr.proten.pms.person.service.dto.MeView;
import kr.proten.pms.person.service.dto.PersonRef;
import kr.proten.pms.person.service.entity.PermissionGroup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 계정 조회 — 신원은 인력 조회와 같은 표현(PersonRef)을 쓰고, 여기에 권한 그룹
 * 플래그를 더한다. 표현을 공유하는 이유는 이름·조직·직급 해석 규칙이 갈라지지
 * 않게 하려는 것이다(PersonRefFactory 주석과 같은 근거).
 */
@Service
@Transactional(readOnly = true)
public class MeQueryServiceImpl implements MeQueryService {
    private final RequesterResolver requesterResolver;
    private final PersonRefFactory personRefFactory;

    public MeQueryServiceImpl(
            RequesterResolver requesterResolver,
            PersonRefFactory personRefFactory) {
        this.requesterResolver = requesterResolver;
        this.personRefFactory = personRefFactory;
    }

    public MeView me(long callerPersonId) {
        Requester requester = requesterResolver.resolve(callerPersonId);
        PersonRef identity = personRefFactory.toRef(requester.person());
        PermissionGroup group = requester.group();

        return new MeView(
                identity.id(),
                identity.name(),
                identity.orgUnit(),
                identity.grade(),
                group.getName(),
                group.getVisibilityScope().name(),
                group.isCreateProject(),
                group.isManageContracts(),
                group.isManageAllProjects(),
                group.isManageOrg());
    }
}
