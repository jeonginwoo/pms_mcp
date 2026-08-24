package kr.proten.pms.person.service.impl;

import java.util.List;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.Person;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.service.entity.PermissionGroup;
import kr.proten.pms.person.service.impl.requester.RequesterResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 밖 기능 플래그 판정 (상위 PRD §4-3).
 *
 * 플래그는 §4-3이 컬럼 4종으로 못 박은 값이라 저장은 엔티티 컬럼으로 두고,
 * 계약 열거형(OrgPermission)과의 대응은 이 서비스가 한 번만 처리한다 — 엔티티는
 * 계약 어휘를 모르는 쪽이 계층이 깨끗하다. 플래그가 늘면 컬럼·상수·이 분기가
 * 함께 늘고, 컴파일러가 누락된 분기를 잡아 준다.
 */
@Service
@Transactional(readOnly = true)
public class OrgPermissionServiceImpl implements OrgPermissionService {
    private final RequesterResolver requesterResolver;
    private final PersonRepository personRepository;

    public OrgPermissionServiceImpl(
            RequesterResolver requesterResolver, PersonRepository personRepository) {
        this.requesterResolver = requesterResolver;
        this.personRepository = personRepository;
    }

    @Override
    public boolean has(long callerPersonId, OrgPermission permission) {
        return granted(requesterResolver.resolve(callerPersonId).group(), permission);
    }

    @Override
    public List<Long> findColleaguesWith(long personId, OrgPermission permission) {
        Person target = personRepository.findByIdAndActiveTrue(personId).orElse(null);

        if (target == null) {
            // 없는·비활성 인원의 동료를 묻는 것은 답이 없는 질문이다 — 예외 대신 빈 목록.
            // 호출자(알림 적재)는 "보낼 사람이 없다"와 같은 처리를 하면 된다
            return List.of();
        }

        return personRepository.findByOrgUnitIdAndActiveTrue(target.getOrgUnitId()).stream()
                .filter(colleague -> !colleague.getId().equals(target.getId()))
                .filter(colleague -> !colleague.isSystem())
                .filter(colleague -> granted(groupOf(colleague), permission))
                .map(Person::getId)
                .toList();
    }

    private PermissionGroup groupOf(Person person) {
        return requesterResolver.resolve(person.getId()).group();
    }

    private static boolean granted(PermissionGroup group, OrgPermission permission) {
        return switch (permission) {
            case CREATE_PROJECT -> group.isCreateProject();
            case MANAGE_CONTRACTS -> group.isManageContracts();
            case MANAGE_ALL_PROJECTS -> group.isManageAllProjects();
            case MANAGE_ORG -> group.isManageOrg();
        };
    }
}
