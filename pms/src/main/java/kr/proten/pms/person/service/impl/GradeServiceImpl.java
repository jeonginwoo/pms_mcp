package kr.proten.pms.person.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.PersonRepository;
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
 * id는 **시퀀스**에서 받는다(2026-08-24): 사용 중이 아니면 하드 삭제되므로
 * `max(id)+1`은 삭제된 id를 다시 내주고, 그 직급을 가리키던 비활성 인원과 감사
 * 로그가 엉뚱한 직급을 가리키게 된다. 규칙 원본은 PRD-pms 부록 B, 사고 선례는 조직 노드다.
 */
@Service
@Transactional
public class GradeServiceImpl implements GradeService {
    private final GradeRepository gradeRepository;
    private final PersonRepository personRepository;
    private final OrgManagePermission orgManagePermission;
    private final PersonAuditRecorder auditRecorder;

    public GradeServiceImpl(
            GradeRepository gradeRepository,
            PersonRepository personRepository,
            OrgManagePermission orgManagePermission,
            PersonAuditRecorder auditRecorder) {
        this.gradeRepository = gradeRepository;
        this.personRepository = personRepository;
        this.orgManagePermission = orgManagePermission;
        this.auditRecorder = auditRecorder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReferenceItem> list(long callerPersonId) {
        orgManagePermission.require(callerPersonId);

        return gradeRepository.findAll().stream()
                .sorted(Comparator.comparing(Grade::getId))
                .map(grade -> new ReferenceItem(grade.getId(), grade.getName()))
                .toList();
    }

    @Override
    public GradeDetail create(long callerPersonId, GradeCommand command) {
        orgManagePermission.require(callerPersonId);

        Grade grade = gradeRepository.save(
                Grade.of(gradeRepository.nextId(), name(command.name()), coeff(command.coeff())));
        auditRecorder.gradeCreated(callerPersonId, grade);

        return detailOf(grade);
    }

    @Override
    public GradeDetail update(long callerPersonId, GradeCommand command) {
        orgManagePermission.require(callerPersonId);

        Grade grade = require(command.gradeId());
        // 바꾸기 직전에 떠 둔다 — 바뀐 필드만 이력에 남는다
        Map<String, Object> before = auditRecorder.snapshot(grade);
        grade.update(name(command.name()), coeff(command.coeff()));
        auditRecorder.gradeChanged(callerPersonId, grade, before);

        // 보정 가동률은 매 조회 계산이라 다음 조회부터 새 계수를 쓴다 — 재계산 호출이 없다(E4-2)
        return detailOf(grade);
    }

    @Override
    public void delete(long callerPersonId, long gradeId) {
        orgManagePermission.require(callerPersonId);

        Grade grade = require(gradeId);

        // 쓰는 사람이 있으면 거절한다 — 지우면 그 사람의 직급·보정 계수가 사라진다 (E4-3)
        if (personRepository.existsByGradeId(gradeId)) {
            throw new ConflictException(ErrorCode.IN_USE, "이 직급을 쓰는 인원이 있습니다");
        }

        auditRecorder.gradeDeleted(callerPersonId, grade);
        gradeRepository.delete(grade);
    }

    /** 없는 직급은 404다 — 참조 데이터라 숨길 것이 없지만 부재의 답은 같다. */
    private Grade require(Long gradeId) {
        if (gradeId == null) {
            throw new ValidationException("직급 id는 필수입니다", "gradeId");
        }

        return gradeRepository.findById(gradeId).orElseThrow(NotFoundException::new);
    }

    private static String name(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("직급명은 필수입니다", "name");
        }

        return value.trim();
    }

    /**
     * 계수는 <b>양수</b>여야 한다 — 0이면 보정 가동률이 전부 0이 되고 음수는 뜻이 없다.
     * 상한은 두지 않는다: 부록 B의 최대가 대표이사 2.0이지만 그것은 시드 값이지 규칙이 아니다.
     */
    private static double coeff(double value) {
        if (!(value > 0)) {
            throw new ValidationException("직급 계수는 0보다 커야 합니다", "coeff");
        }

        return value;
    }

    private static GradeDetail detailOf(Grade grade) {
        return new GradeDetail(grade.getId(), grade.getName(), grade.getCoeff(),
                grade.getVersion());
    }

}
