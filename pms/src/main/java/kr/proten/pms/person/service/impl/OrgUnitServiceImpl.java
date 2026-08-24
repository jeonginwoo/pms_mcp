package kr.proten.pms.person.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.NotImplementedException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.OrgUnitService;
import kr.proten.pms.person.service.dto.OrgUnitView;
import kr.proten.pms.person.service.entity.OrgUnit;
import kr.proten.pms.person.service.entity.Person;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직 트리 관리 유스케이스 — AC E3-3.
 *
 * 삭제 판정은 "빈 노드인가" 하나다: 소속 인원(활성)이나 하위 노드가 있으면 409다.
 * 프로젝트는 조직 노드를 참조하지 않으므로(§4 필드 목록 — 프로젝트의 조직 귀속은
 * 배정 인원으로 파생된다) 검사 대상이 아니다.
 */
@Service
@Transactional
public class OrgUnitServiceImpl implements OrgUnitService {
    private final OrgUnitRepository orgUnitRepository;
    private final PersonRepository personRepository;
    private final OrgManagePermission orgManagePermission;
    private final PersonAuditRecorder personAuditRecorder;

    public OrgUnitServiceImpl(
            OrgUnitRepository orgUnitRepository,
            PersonRepository personRepository,
            OrgManagePermission orgManagePermission,
            PersonAuditRecorder personAuditRecorder) {
        this.orgUnitRepository = orgUnitRepository;
        this.personRepository = personRepository;
        this.orgManagePermission = orgManagePermission;
        this.personAuditRecorder = personAuditRecorder;
    }

    /**
     * ASSUMPTION: 노드·인원을 한 번에 올려 메모리에서 센다 — 조직 17노드·인원 44명
     * 규모의 참조 데이터이고(PersonRefFactory가 같은 이유로 같은 선택을 했다) 노드마다
     * count 질의를 내면 노드 수만큼 왕복한다. 수백 노드가 되면 그룹 질의로 바꾼다.
     */
    @Transactional(readOnly = true)
    public List<OrgUnitView> list(long callerPersonId) {
        orgManagePermission.require(callerPersonId);

        List<OrgUnit> units = orgUnitRepository.findAll();
        Map<Long, Long> memberCounts = personRepository.findByActiveTrue().stream()
                .collect(Collectors.groupingBy(Person::getOrgUnitId, Collectors.counting()));
        Map<Long, Long> childCounts = units.stream()
                .filter(unit -> !unit.isRoot())
                .collect(Collectors.groupingBy(OrgUnit::getParentId, Collectors.counting()));

        return units.stream()
                .sorted(Comparator.comparing(OrgUnit::getId))
                .map(unit -> toView(unit, memberCounts, childCounts))
                .toList();
    }

    /**
     * 노드 신설 (AC E3-1) — 임의 깊이를 허용한다(2단 고정 해제).
     * 부모가 없으면(null) 회사 root가 되는데, 이미 root가 있는 트리에 두 번째 root를
     * 만들면 부문 가시성 계산이 갈라지므로 거절한다(가시성은 root 직계 자식 기준 — §4-3).
     */
    public OrgUnitView create(long callerPersonId, Long parentId, String name) {
        orgManagePermission.require(callerPersonId);
        requireText(name);
        requireValidParent(parentId);

        OrgUnit saved = orgUnitRepository.save(
                OrgUnit.of(orgUnitRepository.nextId(), parentId, name.trim()));
        personAuditRecorder.orgUnitCreated(callerPersonId, saved);

        return new OrgUnitView(saved.getId(), saved.getParentId(), saved.getName(), 0, 0, true);
    }

    /**
     * 노드 개명 — **골격만 있고 로직은 아직 없다** (2026-08-22).
     *
     * 이미 정해져 있는 것: 이름을 복사해 둔 컬럼이 없으므로 소속 인원·프로젝트의 표시는
     * 저절로 따라온다(E3-2 — 비정규화 금지). 감사 action은 `UPDATE`이고, 회사(root)의
     * 이름도 같은 경로로 바꾼다.
     *
     * 권한 판정은 골격 단계에서도 실제로 한다 — 없는 것은 로직이지 권한이 아니다.
     *
     * **같은 부모 아래 이름 중복은 막지 않는다**(2026-08-24 사용자 결정): AC에 없는 규칙을
     * 구현이 지어내지 않는다. 조직은 어디서나 id로 참조되고 화면은 트리로 보여 주므로
     * 동명이 실무적으로 깨뜨리는 것이 없다 — 스키마에도 유니크 제약이 없다(V1).
     * 필요해지면 AC를 먼저 고치고 생성(E3-1)까지 같은 규칙으로 연다.
     */
    public OrgUnitView rename(long callerPersonId, long orgUnitId, String name) {
        orgManagePermission.require(callerPersonId);
        requireText(name);

        OrgUnit target = orgUnitRepository.findById(orgUnitId).orElseThrow(NotFoundException::new);
        String before = target.getName();

        target.rename(name.trim());
        personAuditRecorder.orgUnitRenamed(callerPersonId, target, before);

        // 소속 인원·프로젝트는 orgUnitId로 참조하므로 표시가 저절로 따라온다(E3-2).
        // 개수는 목록 조회가 채우는 값이라 단건 응답에서는 0이다 — 생성(E3-1)과 같은 형태.
        return new OrgUnitView(target.getId(), target.getParentId(), target.getName(), 0, 0, false);
    }

    public void delete(long callerPersonId, long orgUnitId) {
        orgManagePermission.require(callerPersonId);

        OrgUnit target = orgUnitRepository.findById(orgUnitId).orElseThrow(NotFoundException::new);
        requireEmpty(orgUnitId);

        orgUnitRepository.delete(target);
        personAuditRecorder.orgUnitDeleted(callerPersonId, target);
    }

    private OrgUnitView toView(
            OrgUnit unit,
            Map<Long, Long> memberCounts,
            Map<Long, Long> childCounts) {
        long members = memberCounts.getOrDefault(unit.getId(), 0L);
        long children = childCounts.getOrDefault(unit.getId(), 0L);

        return new OrgUnitView(unit.getId(), unit.getParentId(), unit.getName(), members, children,
                members == 0 && children == 0);
    }

    private void requireText(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("조직명은 필수입니다", "name");
        }
    }

    private void requireValidParent(Long parentId) {
        if (parentId == null) {
            requireNoRootYet();

            return;
        }

        if (!orgUnitRepository.existsById(parentId)) {
            throw new UnprocessableException(ErrorCode.REF_NOT_FOUND, "없는 상위 조직입니다: " + parentId);
        }
    }

    private void requireNoRootYet() {
        boolean rootExists = orgUnitRepository.findAll().stream().anyMatch(OrgUnit::isRoot);

        if (rootExists) {
            throw new ConflictException(ErrorCode.DUPLICATE_ROOT,
                    "회사(root) 노드는 하나뿐입니다 — 상위 조직을 지정하세요");
        }
    }

    private void requireEmpty(long orgUnitId) {
        long members = personRepository.countByOrgUnitIdAndActiveTrue(orgUnitId);
        long children = orgUnitRepository.countByParentId(orgUnitId);

        if (members > 0 || children > 0) {
            throw new ConflictException(ErrorCode.IN_USE,
                    "소속 인원 %d명·하위 조직 %d개가 있어 삭제할 수 없습니다".formatted(
                            members, children));
        }
    }
}
