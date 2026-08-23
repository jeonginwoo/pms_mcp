package kr.proten.pms.maintenance.repository;

import java.util.Collection;
import java.util.List;
import kr.proten.pms.maintenance.service.entity.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 이슈 코멘트 저장소 — append-only(D3-3)라 저장·조회만 있고 수정·삭제 경로가 없다.
 * 시간순(오래된 것부터)이 기본 정렬이다: 코멘트는 대화 흐름이라 역순으로 읽지 않는다.
 */
public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {

    List<IssueComment> findByIssueIdInOrderByCreatedAtAsc(Collection<Long> issueIds);
}
