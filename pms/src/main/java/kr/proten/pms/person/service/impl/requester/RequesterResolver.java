package kr.proten.pms.person.service.impl.requester;

import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.entity.PermissionGroup;
import kr.proten.pms.person.service.entity.Person;
import org.springframework.stereotype.Component;

/**
 * 호출자 personId → 요청자 컨텍스트 해석.
 *
 * ASSUMPTION: 없는·비활성 호출자는 404로 수렴시킨다. 인증이 들어오면 이 경로는
 * 토큰 문제(401)로 승격되지만, 지금은 호출자 식별이 서비스 파라미터라
 * "존재하지 않는 대상"과 구분해 알려 줄 이유가 없다 — 은닉 쪽이 안전하다.
 */
@Component
public class RequesterResolver {
    private final PersonRepository personRepository;
    private final PermissionGroupRepository permissionGroupRepository;

    public RequesterResolver(
            PersonRepository personRepository,
            PermissionGroupRepository permissionGroupRepository) {
        this.personRepository = personRepository;
        this.permissionGroupRepository = permissionGroupRepository;
    }

    public Requester resolve(long callerPersonId) {
        Person person = personRepository.findByIdAndActiveTrue(callerPersonId)
                .orElseThrow(NotFoundException::new);
        PermissionGroup group = permissionGroupRepository.findById(person.getGroupId())
                .orElseThrow(() -> new IllegalStateException(
                        "권한 그룹 없음: " + person.getGroupId()));

        return new Requester(person, group);
    }
}
