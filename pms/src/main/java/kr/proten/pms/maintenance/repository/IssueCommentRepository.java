package kr.proten.pms.maintenance.repository;

import java.util.Collection;
import java.util.List;
import kr.proten.pms.maintenance.service.entity.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 이슈 코멘트 저장소 — append-only(D3-3)라 저장·조회만 있고 수정·삭제 경로가 없다.
 * 시간순(오래된 것부터)이 기본 정렬이다: 코멘트는 대화 흐름이라 역순으로 읽지 않는다.
 *
 * <p>{@code createdAt} 뒤에 <b>id를 타이브레이커로 둔다</b>: 한 트랜잭션에서 두 코멘트가
 * 같은 마이크로초에 떨어지면 순서가 미정이 되고(이슈 검색 질의가 같은 이유로 {@code i.id desc}를
 * 갖고 있다), 대화 흐름을 뒤집힌 채로 보여 줄 수 있다.
 */
public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {

    List<IssueComment> findByIssueIdInOrderByCreatedAtAscIdAsc(Collection<Long> issueIds);
}
