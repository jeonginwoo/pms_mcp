package kr.proten.pms.person.service;

import java.util.List;
import kr.proten.pms.person.service.dto.GradeCommand;
import kr.proten.pms.person.service.dto.GradeDetail;

/**
 * 직급 관리 유스케이스 — US-E4 (2026-08-09 ⑤ 채택).
 *
 * 판정자는 EPIC E 공통인 "사용자/조직/권한 관리" 플래그다.
 * 계수 변경이 곧바로 보정 가동률에 반영되는 것은 캐시를 두지 않았기 때문이다
 * (2026-08-06 결정) — 여기서 따로 재계산을 부르지 않는다.
 */
public interface GradeService {

    /** 선택 목록 — 인력 등록 폼이 고를 직급들. 관리 화면과 같은 판정을 거친다. */
    List<GradeDetail> list(long callerPersonId);

    /** 직급을 만든다 (AC E4-1). */
    GradeDetail create(long callerPersonId, GradeCommand command);

    /** 직급을 수정한다 (AC E4-2) — coeff 변경은 다음 가동률 조회부터 반영된다. */
    GradeDetail update(long callerPersonId, GradeCommand command);

    /** 직급을 삭제한다 — 쓰는 인원이 있으면 거절한다 (AC E4-3 `409 IN_USE`). */
    void delete(long callerPersonId, long gradeId);
}
