package kr.proten.pms.person.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.repository.OrgUnitRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.OrgPermissionService;
import kr.proten.pms.person.service.OrgUnitService;
import kr.proten.pms.person.service.dto.OrgPermission;
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
    private final OrgPermissionService orgPermissionService;
    private final PersonAuditRecorder personAuditRecorder;

    public OrgUnitServiceImpl(
            OrgUnitRepository orgUnitRepository,
            PersonRepository personRepository,
            OrgPermissionService orgPermissionService,
            PersonAuditRecorder personAuditRecorder) {
        this.orgUnitRepository = orgUnitRepository;
        this.personRepository = personRepository;
        this.orgPermissionService = orgPermissionService;
        this.personAuditRecorder = personAuditRecorder;
    }

    /**
     * ASSUMPTION: 노드·인원을 한 번에 올려 메모리에서 센다 — 조직 17노드·인원 44명
     * 규모의 참조 데이터이고(PersonRefFactory가 같은 이유로 같은 선택을 했다) 노드마다
     * count 질의를 내면 노드 수만큼 왕복한다. 수백 노드가 되면 그룹 질의로 바꾼다.
     */
    @Transactional(readOnly = true)
    public List<OrgUnitView> list(long callerPersonId) {
        requireManageOrg(callerPersonId);

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
        requireManageOrg(callerPersonId);
        requireText(name);
        requireValidParent(parentId);

        OrgUnit saved = orgUnitRepository.save(
                OrgUnit.of(orgUnitRepository.nextId(), parentId, name.trim()));
        personAuditRecorder.orgUnitCreated(callerPersonId, saved);

        return new OrgUnitView(saved.getId(), saved.getParentId(), saved.getName(), 0, 0, true);
    }

    public void delete(long callerPersonId, long orgUnitId) {
        requireManageOrg(callerPersonId);

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

    private void requireManageOrg(long callerPersonId) {
        if (!orgPermissionService.has(callerPersonId, OrgPermission.MANAGE_ORG)) {
            throw new ForbiddenException("사용자·조직 관리 권한이 없습니다");
        }
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
            throw new UnprocessableException("REF_NOT_FOUND", "없는 상위 조직입니다: " + parentId);
        }
    }

    private void requireNoRootYet() {
        boolean rootExists = orgUnitRepository.findAll().stream().anyMatch(OrgUnit::isRoot);

        if (rootExists) {
            throw new ConflictException("DUPLICATE_ROOT",
                    "회사(root) 노드는 하나뿐입니다 — 상위 조직을 지정하세요");
        }
    }

    private void requireEmpty(long orgUnitId) {
        long members = personRepository.countByOrgUnitIdAndActiveTrue(orgUnitId);
        long children = orgUnitRepository.countByParentId(orgUnitId);

        if (members > 0 || children > 0) {
            throw new ConflictException("IN_USE",
                    "소속 인원 %d명·하위 조직 %d개가 있어 삭제할 수 없습니다".formatted(
                            members, children));
        }
    }
}
