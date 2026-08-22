package kr.proten.pms.person.service.impl;

import java.util.Comparator;
import java.util.List;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotImplementedException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.service.GradeService;
import kr.proten.pms.person.service.dto.GradeCommand;
import kr.proten.pms.person.service.dto.GradeDetail;
import kr.proten.pms.person.service.dto.ReferenceItem;
import kr.proten.pms.person.service.entity.Grade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 직급 관리 — 목록은 동작하고 등록·수정·삭제는 아직 골격이다 (2026-08-22).
 *
 * 조회도 관리 플래그를 요구하는 이유: 직급 목록의 쓰임이 인력 등록 폼과 직급 관리
 * 화면 둘뿐이고 둘 다 관리 화면이다 — 일반 사용자가 고를 일이 없다.
 *
 * 쓰기에서 이미 정해져 있는 것:
 * - 검사 순서는 EPIC E 공통 — 권한(403) → 입력·참조(400·422) → 사용 중(409)
 * - 삭제 거절은 "쓰는 인원이 있는가"이고, 판정은 `findAll()`이 아니라 파생 질의로 한다
 *   (conventions §6 — 존재/개수 질문은 `existsBy…`)
 *
 * **권한 판정은 골격 단계에서도 실제로 한다** — 없는 것은 로직이지 권한이 아니다.
 * 관리 플래그 없는 호출자는 501이 아니라 403을 받는다.
 *
 * TODO(E4-1·E4-2·E4-3): `GradeRepository`에는 사용 인원을 세는 경로가 없다.
 *   `PersonRepository.existsByGradeId`가 필요하다 — 같은 모듈이라 경계 문제는 없다.
 */
@Service
@Transactional
public class GradeServiceImpl implements GradeService {
    private final GradeRepository gradeRepository;
    private final OrgPermissionService orgPermissionService;

    public GradeServiceImpl(
            GradeRepository gradeRepository,
            OrgPermissionService orgPermissionService) {
        this.gradeRepository = gradeRepository;
        this.orgPermissionService = orgPermissionService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReferenceItem> list(long callerPersonId) {
        requireManageOrg(callerPersonId);

        return gradeRepository.findAll().stream()
                .sorted(Comparator.comparing(Grade::getId))
                .map(grade -> new ReferenceItem(grade.getId(), grade.getName()))
                .toList();
    }

    @Override
    public GradeDetail create(long callerPersonId, GradeCommand command) {
        requireManageOrg(callerPersonId);

        throw new NotImplementedException("직급 등록 (E4-1)");
    }

    @Override
    public GradeDetail update(long callerPersonId, GradeCommand command) {
        requireManageOrg(callerPersonId);

        throw new NotImplementedException("직급 수정 (E4-2)");
    }

    @Override
    public void delete(long callerPersonId, long gradeId) {
        requireManageOrg(callerPersonId);

        throw new NotImplementedException("직급 삭제 (E4-3)");
    }

    private void requireManageOrg(long callerPersonId) {
        if (!orgPermissionService.has(callerPersonId, OrgPermission.MANAGE_ORG)) {
            throw new ForbiddenException("사용자·조직 관리 권한이 없습니다");
        }
    }
}
