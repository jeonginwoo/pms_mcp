package kr.proten.pms.person.service.impl;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.NotImplementedException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.ProjectCountPort;
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
 * 삭제 판정은 "빈 노드인가" 하나이고 보는 것은 셋이다: 소속 인원(활성)·하위 노드·
 * 그 노드가 PM 소속 노드인 프로젝트.
 *
 * **프로젝트가 판정에 들어온 것은 2026-08-26이다**(사용자 결정). 그전에는 "프로젝트는
 * 조직 노드를 참조하지 않으므로(§4 — 조직 귀속은 배정 인원으로 파생된다) 검사 대상이
 * 아니다"가 근거였는데, 그 문장은 **셀 방법이 없다**는 뜻이었지 세지 않기로 했다는
 * 뜻이 아니었다. `ProjectCountPort`가 PM → 소속 노드로 접는 길을 놓으면서 근거가
 * 사라졌고, AC E3-3의 문면("소속 인원·**프로젝트**·하위 조직")이 그대로 성립한다.
 *
 * 화면 표시와 삭제 판정은 **같은 수**를 읽는다(`projectCountsByOrgUnit` 한 곳) —
 * 갈라 두면 화면이 "프로젝트 14"라고 적어 둔 노드에서 삭제가 성공한다.
 */
@Service
@Transactional
public class OrgUnitServiceImpl implements OrgUnitService {
    private final OrgUnitRepository orgUnitRepository;
    private final PersonRepository personRepository;
    private final ProjectCountPort projectCountPort;
    private final OrgManagePermission orgManagePermission;
    private final PersonAuditRecorder personAuditRecorder;

    public OrgUnitServiceImpl(
            OrgUnitRepository orgUnitRepository,
            PersonRepository personRepository,
            ProjectCountPort projectCountPort,
            OrgManagePermission orgManagePermission,
            PersonAuditRecorder personAuditRecorder) {
        this.orgUnitRepository = orgUnitRepository;
        this.personRepository = personRepository;
        this.projectCountPort = projectCountPort;
        this.orgManagePermission = orgManagePermission;
        this.personAuditRecorder = personAuditRecorder;
    }

    /**
     * ASSUMPTION: 노드·인원을 한 번에 올려 메모리에서 센다 — 조직 18노드·인원 44명
     * 규모의 참조 데이터이고(PersonRefFactory가 같은 이유로 같은 선택을 했다) 노드마다
     * count 질의를 내면 노드 수만큼 왕복한다. 수백 노드가 되면 그룹 질의로 바꾼다.
     *
     * 인원 목록은 **비활성까지** 올린다 — 인원 수는 활성만 세고 프로젝트 수는 퇴사한
     * PM의 것도 세야 해서 기준이 다르다. 그 두 수 때문에 목록을 두 번 올리지 않는다.
     */
    @Transactional(readOnly = true)
    public List<OrgUnitView> list(long callerPersonId) {
        orgManagePermission.require(callerPersonId);

        List<OrgUnit> units = orgUnitRepository.findAll();
        List<Person> people = personRepository.findAll();
        Map<Long, Long> memberCounts = people.stream()
                .filter(Person::isActive)
                .collect(Collectors.groupingBy(Person::getOrgUnitId, Collectors.counting()));
        Map<Long, Long> childCounts = units.stream()
                .filter(unit -> !unit.isRoot())
                .collect(Collectors.groupingBy(OrgUnit::getParentId, Collectors.counting()));
        Map<Long, Long> projectCounts = projectCountsByOrgUnit(people);

        return units.stream()
                .sorted(Comparator.comparing(OrgUnit::getId))
                .map(unit -> toView(unit, memberCounts, childCounts, projectCounts))
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

        return new OrgUnitView(saved.getId(), saved.getParentId(), saved.getName(), 0, 0, 0, true);
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
        return new OrgUnitView(
                target.getId(), target.getParentId(), target.getName(), 0, 0, 0, false);
    }

    /**
     * 노드 이동 (AC E3-5·E3-6) — 조직 개편을 화면에서 할 수 있게 하는 경로다.
     *
     * 소속 인원·프로젝트는 옮기지 않는다. 옮길 것이 없기 때문이다: 둘 다 orgUnitId로
     * 참조하므로 노드가 움직이면 경로가 함께 움직인다(개명 E3-2와 같은 원리 —
     * 비정규화된 이름·경로 컬럼이 없는 것이 이 AC들이 성립하는 이유다).
     *
     * 가시성은 다음 요청부터 새 경로를 따른다 — 캐시가 없고 매 조회 계산이다.
     * 부문(root 직계 자식)이 바뀌는 이동은 `PersonRef.division` 파생값을 바꾸므로
     * 프로젝트·가동률 응답의 부문 표시도 함께 움직인다(같은 트리를 한 규칙으로 읽는다).
     */
    public OrgUnitView move(long callerPersonId, long orgUnitId, Long parentId) {
        orgManagePermission.require(callerPersonId);

        OrgUnit target = orgUnitRepository.findById(orgUnitId).orElseThrow(NotFoundException::new);

        if (target.isRoot()) {
            throw new ValidationException(
                    "회사(root)는 옮길 수 없습니다 — 부문 가시성이 root 직계 자식 기준입니다",
                    "orgUnitId");
        }

        // null 부모 = 두 번째 root 요청이라 생성과 같은 판정을 그대로 쓴다(409 DUPLICATE_ROOT)
        requireValidParent(parentId);
        requireNotInOwnSubtree(orgUnitId, parentId);

        Long before = target.getParentId();
        target.moveTo(parentId);
        personAuditRecorder.orgUnitMoved(callerPersonId, target, before);

        // 개수는 목록 조회가 채우는 값이라 단건 응답에서는 0이다 — 생성·개명과 같은 형태
        return new OrgUnitView(
                target.getId(), target.getParentId(), target.getName(), 0, 0, 0, false);
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
            Map<Long, Long> childCounts,
            Map<Long, Long> projectCounts) {
        long members = memberCounts.getOrDefault(unit.getId(), 0L);
        long children = childCounts.getOrDefault(unit.getId(), 0L);
        long projects = projectCounts.getOrDefault(unit.getId(), 0L);

        return new OrgUnitView(unit.getId(), unit.getParentId(), unit.getName(), members, children,
                projects, members == 0 && children == 0 && projects == 0);
    }

    /**
     * 인원 목록을 PM 소속 노드별 프로젝트 수로 접는다 — 이 규칙이 사는 유일한 자리다
     * (PRD-pms §12 정의: "그 노드가 PM 소속 노드인, 삭제되지 않은 프로젝트 수").
     *
     * 접는 쪽이 person인 이유는 {@link ProjectCountPort}의 javadoc에 있다: "누가 어느
     * 노드에 속하는가"는 person의 지식이라, 노드별로 받아 오면 같은 규칙이 project에도
     * 생긴다.
     *
     * 목록 조회는 전원을, 삭제 판정은 그 노드 인원만을 넘긴다 — **규칙은 하나이고
     * 범위만 다르다**. 프로젝트가 0건인 PM은 맵에 키를 만들지 않으므로, 인원만 있고
     * 프로젝트가 없는 노드는 여기서 키 없이 빠진다(호출자가 0으로 읽는다).
     */
    private Map<Long, Long> projectCountsByOrgUnit(List<Person> people) {
        if (people.isEmpty()) {
            // 인원이 없으면 PM도 없다 — 빈 노드 삭제(E3-3의 흔한 갈래)에서 질의를 아낀다
            return Map.of();
        }
        Map<Long, Long> byManager = projectCountPort.countByManager();
        Map<Long, Long> byOrgUnit = new HashMap<>();

        for (Person person : people) {
            long managed = byManager.getOrDefault(person.getId(), 0L);
            if (managed > 0) {
                byOrgUnit.merge(person.getOrgUnitId(), managed, Long::sum);
            }
        }
        return byOrgUnit;
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

    /**
     * 자기 자신·자기 subtree 안으로는 옮길 수 없다 (AC E3-6).
     *
     * 새 부모에서 **위로** 올라가며 대상을 만나는지 본다 — subtree를 아래로 훑는 것보다
     * 짧다(경로 길이만큼이고, 노드 하나에 부모는 하나뿐이라 분기가 없다).
     *
     * 걸음 수를 노드 수로 묶는 이유: 이미 순환이 들어 있는 데이터에서는 위로 올라가도
     * 끝나지 않는다. 그 상태를 무한 루프로 알게 되면 늦으므로 거절로 드러낸다.
     */
    private void requireNotInOwnSubtree(long orgUnitId, Long newParentId) {
        Map<Long, Long> parentOf = orgUnitRepository.findAll().stream()
                .filter(unit -> !unit.isRoot())
                .collect(Collectors.toMap(OrgUnit::getId, OrgUnit::getParentId));

        Long current = newParentId;

        for (int step = 0; current != null; step++) {
            if (current == orgUnitId) {
                throw new ValidationException(
                        "자기 자신이나 자기 하위 조직 아래로는 옮길 수 없습니다", "parentId");
            }

            if (step > parentOf.size()) {
                throw new ValidationException("조직 트리에 순환이 있습니다", "parentId");
            }

            current = parentOf.get(current);
        }
    }

    /**
     * AC E3-3 — 소속 인원·프로젝트·하위 조직 중 하나라도 있으면 409다.
     *
     * 프로젝트를 세는 기준은 목록 조회와 같다(`projectCountsByOrgUnit`). 여기서만
     * "진행 중인 것만" 같은 조건을 더하면 화면이 보여 준 수와 막는 수가 갈린다.
     *
     * 인원은 활성만, 프로젝트는 **비활성 PM의 것까지** 센다 — 어긋나 보이지만 이것이
     * 정확히 이 검사가 필요한 이유다: 퇴사 처리된 PM만 남은 노드는 인원 0으로 보이고
     * 그동안 삭제됐다. 스키마에 FK가 없어(V1~V16) DB도 막지 않으므로, 지워진 노드를
     * 가리키는 `org_unit_id`와 그를 통한 프로젝트 귀속이 그대로 남았다.
     */
    private void requireEmpty(long orgUnitId) {
        long members = personRepository.countByOrgUnitIdAndActiveTrue(orgUnitId);
        long children = orgUnitRepository.countByParentId(orgUnitId);
        long projects = projectCountsByOrgUnit(personRepository.findByOrgUnitId(orgUnitId))
                .getOrDefault(orgUnitId, 0L);

        if (members > 0 || children > 0 || projects > 0) {
            throw new ConflictException(ErrorCode.IN_USE,
                    "소속 인원 %d명·프로젝트 %d건·하위 조직 %d개가 있어 삭제할 수 없습니다"
                            .formatted(members, projects, children));
        }
    }
}
