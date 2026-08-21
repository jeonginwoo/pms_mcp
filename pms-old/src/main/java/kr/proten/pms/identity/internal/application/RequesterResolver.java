package kr.proten.pms.identity.internal.application;

import kr.proten.pms.identity.internal.domain.PermissionGroup;
import kr.proten.pms.identity.internal.domain.Person;
import kr.proten.pms.identity.internal.domain.repository.PermissionGroupRepository;
import kr.proten.pms.identity.internal.domain.repository.PersonRepository;
import org.springframework.stereotype.Component;

/**
 * 토큰 personId → 요청자 컨텍스트 해석.
 * 사람이 없거나 비활성이면 토큰 문제로 취급한다(401 — MeQueryService와 동일 규칙,
 * E2-3 soft 삭제 = 접근 차단).
 */
@Component
public class RequesterResolver {
    // 본인 조회
    private final PersonRepository personRepository;
    // 권한 그룹 조회
    private final PermissionGroupRepository permissionGroupRepository;

    public RequesterResolver(
            PersonRepository personRepository,
            PermissionGroupRepository permissionGroupRepository) {
        this.personRepository = personRepository;
        this.permissionGroupRepository = permissionGroupRepository;
    }

    public Requester resolve(Long personId) {
        Person person = personRepository.findById(personId)
                .filter(Person::active)
                .orElseThrow(InvalidTokenException::new);
        PermissionGroup group = permissionGroupRepository.findById(person.groupId())
                .orElseThrow(() -> new IllegalStateException("권한 그룹 없음: " + person.groupId()));

        return new Requester(person, group);
    }
}
