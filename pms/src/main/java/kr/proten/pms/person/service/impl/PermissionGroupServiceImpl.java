package kr.proten.pms.person.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.service.PermissionGroupService;
import kr.proten.pms.person.service.dto.PermissionGroupCommand;
import kr.proten.pms.person.service.dto.PermissionGroupDetail;
import kr.proten.pms.person.service.entity.PermissionGroup;
import kr.proten.pms.person.service.entity.VisibilityScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 권한 그룹 관리 — 조회·등록·수정·삭제 전량 실구현 (US-E5. 골격은 2026-08-24에 걷혔다).
 *
 * 목록이 `ReferenceItem`이 아니라 `PermissionGroupDetail`인 것은 2026-08-24 변경이다:
 * 부록 A의 권한 그룹 행(n명·가시성·기능 토글·수정·삭제)을 그리려면 id·이름으로는 부족하다.
 *
 * 쓰기에서 이미 정해져 있는 것:
 * - `systemFixed` 그룹은 수정·삭제 둘 다 `422 IMMUTABLE_GROUP`(E5-3) — 관리자 그룹이
 *   편집 가능해지면 마지막 관리자가 스스로를 잠글 수 있다
 * - 소속 인원이 있으면 삭제 `409 IN_USE`(E5-4) — 먼저 E2-2로 그룹을 옮긴다
 * - `visibilityScope` 문자열은 여기서 해석한다 — 모르는 값은 400이 아니라 422다(§7)
 *
 * **권한 판정은 골격 단계에서도 실제로 한다** — 없는 것은 로직이지 권한이 아니다.
 * 관리 플래그 없는 호출자는 501이 아니라 403을 받는다.
 *
 * id는 **시퀀스**에서 받는다(2026-08-24) — 직급과 같은 이유다(하드 삭제가 있는 명시 id
 * 참조 데이터. PRD-pms 부록 B 규칙 · 조직 노드 사고 선례).
 */
@Service
@Transactional
public class PermissionGroupServiceImpl implements PermissionGroupService {
    private final PermissionGroupRepository permissionGroupRepository;
    private final PersonRepository personRepository;
    private final OrgManagePermission orgManagePermission;
    private final PersonAuditRecorder auditRecorder;

    public PermissionGroupServiceImpl(
            PermissionGroupRepository permissionGroupRepository,
            PersonRepository personRepository,
            OrgManagePermission orgManagePermission,
            PersonAuditRecorder auditRecorder) {
        this.permissionGroupRepository = permissionGroupRepository;
        this.personRepository = personRepository;
        this.orgManagePermission = orgManagePermission;
        this.auditRecorder = auditRecorder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionGroupDetail> list(long callerPersonId) {
        orgManagePermission.require(callerPersonId);

        Map<Long, Long> members = memberCounts();

        return permissionGroupRepository.findAll().stream()
                .sorted(Comparator.comparing(PermissionGroup::getId))
                .map(group -> detailOf(group, members.getOrDefault(group.getId(), 0L)))
                .toList();
    }

    @Override
    public PermissionGroupDetail create(long callerPersonId, PermissionGroupCommand command) {
        orgManagePermission.require(callerPersonId);

        // 새 그룹은 절대 systemFixed가 아니다 — 고정은 시드의 관리자 그룹 하나뿐이고,
        // 만들 수 있게 하면 지울 수 없는 그룹을 사용자가 계속 찍어낼 수 있다
        PermissionGroup group = permissionGroupRepository.save(PermissionGroup.of(
                permissionGroupRepository.nextId(),
                name(command.name()),
                scope(command.visibilityScope()),
                command.createProject(),
                command.manageContracts(),
                command.manageAllProjects(),
                command.manageOrg(),
                false));
        auditRecorder.permissionGroupCreated(callerPersonId, group);

        return detailOf(group);
    }

    @Override
    public PermissionGroupDetail update(long callerPersonId, PermissionGroupCommand command) {
        orgManagePermission.require(callerPersonId);

        PermissionGroup group = requireEditable(command.groupId());
        // 2026-08-24 결함 수정 — 받아만 두고 검사하지 않던 version이다(PersonServiceImpl 참조)
        group.requireVersion(command.version());
        Map<String, Object> before = auditRecorder.snapshot(group);
        group.update(
                name(command.name()),
                scope(command.visibilityScope()),
                command.createProject(),
                command.manageContracts(),
                command.manageAllProjects(),
                command.manageOrg());
        PermissionGroup saved = permissionGroupRepository.saveAndFlush(group);
        auditRecorder.permissionGroupChanged(callerPersonId, saved, before);

        // 판정·가시성·404 은닉은 저장된 값이 아니라 이 그룹을 읽어 정해지므로
        // 다음 요청부터 새 정의를 탄다 (E5-2)
        return detailOf(saved);
    }

    @Override
    public void delete(long callerPersonId, long groupId) {
        orgManagePermission.require(callerPersonId);

        PermissionGroup group = requireEditable(groupId);

        // 소속 인원이 있으면 거절 — 먼저 E2-2로 그룹을 옮긴다 (E5-4)
        if (personRepository.existsByGroupId(groupId)) {
            throw new ConflictException(ErrorCode.IN_USE, "이 그룹에 속한 인원이 있습니다");
        }

        auditRecorder.permissionGroupDeleted(callerPersonId, group);
        permissionGroupRepository.delete(group);
    }

    /**
     * 수정·삭제가 가능한 그룹인지 (AC E5-3).
     *
     * <p>{@code systemFixed}(관리자)를 막는 이유는 자기 잠금이다 — 관리자 그룹의 플래그를
     * 끄거나 그룹을 지우면 <b>마지막 관리자가 관리 권한을 잃고 되돌릴 방법이 없다</b>.
     * 그래서 이 판정이 인원 존재 검사(409)보다 <b>먼저</b> 온다: 인원이 0인 관리자 그룹도
     * 여전히 지울 수 없다.
     */
    private PermissionGroup requireEditable(Long groupId) {
        if (groupId == null) {
            throw new ValidationException("권한 그룹 id는 필수입니다", "groupId");
        }

        PermissionGroup group = permissionGroupRepository.findById(groupId)
                .orElseThrow(NotFoundException::new);

        if (group.isSystemFixed()) {
            throw new UnprocessableException(
                    ErrorCode.IMMUTABLE_GROUP, "시스템 고정 그룹은 수정·삭제할 수 없습니다");
        }

        return group;
    }

    private static String name(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("그룹명은 필수입니다", "name");
        }

        return value.trim();
    }

    /**
     * 가시성 scope 해석 — 모르는 값은 <b>400이 아니라 422</b>다(§7 · 골격 주석).
     * 형식은 맞는데 참조가 성립하지 않는 경우이기 때문이다.
     */
    private static VisibilityScope scope(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("가시성 범위는 필수입니다", "visibilityScope");
        }

        for (VisibilityScope candidate : VisibilityScope.values()) {
            if (candidate.name().equalsIgnoreCase(raw.trim())) {
                return candidate;
            }
        }

        throw new UnprocessableException(ErrorCode.REF_NOT_FOUND,
                "가시성 범위는 COMPANY/DIVISION/TEAM/SELF 중 하나여야 합니다");
    }

    /** 그룹별 인원 수 — 목록에서 행마다 세지 않으려고 한 번에 묶어 받는다. */
    private Map<Long, Long> memberCounts() {
        return personRepository.countByGroup().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private PermissionGroupDetail detailOf(PermissionGroup group) {
        return detailOf(group, personRepository.countByGroupId(group.getId()));
    }

    private static PermissionGroupDetail detailOf(PermissionGroup group, long memberCount) {
        return new PermissionGroupDetail(
                group.getId(),
                group.getName(),
                group.getVisibilityScope().name(),
                group.isCreateProject(),
                group.isManageContracts(),
                group.isManageAllProjects(),
                group.isManageOrg(),
                group.isSystemFixed(),
                memberCount,
                group.getVersion());
    }

}
