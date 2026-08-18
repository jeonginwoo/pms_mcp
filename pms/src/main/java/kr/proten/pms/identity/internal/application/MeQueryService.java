package kr.proten.pms.identity.internal.application;

import kr.proten.pms.identity.internal.domain.Person;
import kr.proten.pms.identity.internal.domain.repository.PersonRepository;
import kr.proten.pms.identity.internal.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 조회 — MCP whoami와 같은 서비스가 될 접점 (H1-1).
 * ASSUMPTION: PMS-M1a에서는 인증 관통 확인용 최소 응답(personId·이름·email)만.
 * 조직 경로(팀·부문)·권한 그룹명 동봉은 H1-1 완성 시(PMS-M1d) 추가한다.
 */
@Service
@Transactional(readOnly = true)
public class MeQueryService {
    // 본인 정보 조회
    private final PersonRepository personRepository;
    // email 동봉용 계정 조회
    private final UserRepository userRepository;

    public MeQueryService(PersonRepository personRepository, UserRepository userRepository) {
        this.personRepository = personRepository;
        this.userRepository = userRepository;
    }

    /** 인증 관통 확인용 최소 응답. */
    public record MeSummary(Long personId, String name, String email) {
    }

    /**
     * 토큰의 personId로 본인 요약을 조회합니다.
     * 토큰이 가리키는 사람이 없거나 비활성이면 토큰 문제로 취급합니다(401).
     */
    public MeSummary getMe(Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(InvalidTokenException::new);

        if (!person.active()) {
            throw new InvalidTokenException();
        }

        String email = userRepository.findByPersonId(personId)
                .map(user -> user.email())
                .orElse(null);

        return new MeSummary(person.id(), person.name(), email);
    }
}
