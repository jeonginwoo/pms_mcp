package kr.proten.pms.person.service.impl;

import java.util.Comparator;
import java.util.List;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.common.exception.NotImplementedException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.service.PermissionGroupService;
import kr.proten.pms.person.service.dto.PermissionGroupCommand;
import kr.proten.pms.person.service.dto.PermissionGroupDetail;
import kr.proten.pms.person.service.dto.ReferenceItem;
import kr.proten.pms.person.service.entity.PermissionGroup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 권한 그룹 관리 — 목록은 동작하고 등록·수정·삭제는 아직 골격이다 (2026-08-22).
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
 * TODO(E5-1·E5-2·E5-3·E5-4): 소속 인원 존재 판정에 `PersonRepository.existsByGroupId`가
 *   필요하다.
 */
@Service
@Transactional
public class PermissionGroupServiceImpl implements PermissionGroupService {
    private final PermissionGroupRepository permissionGroupRepository;
    private final OrgPermissionService orgPermissionService;

    public PermissionGroupServiceImpl(
            PermissionGroupRepository permissionGroupRepository,
            OrgPermissionService orgPermissionService) {
        this.permissionGroupRepository = permissionGroupRepository;
        this.orgPermissionService = orgPermissionService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReferenceItem> list(long callerPersonId) {
        requireManageOrg(callerPersonId);

        return permissionGroupRepository.findAll().stream()
                .sorted(Comparator.comparing(PermissionGroup::getId))
                .map(group -> new ReferenceItem(group.getId(), group.getName()))
                .toList();
    }

    @Override
    public PermissionGroupDetail create(long callerPersonId, PermissionGroupCommand command) {
        requireManageOrg(callerPersonId);

        throw new NotImplementedException("권한 그룹 등록 (E5-1)");
    }

    @Override
    public PermissionGroupDetail update(long callerPersonId, PermissionGroupCommand command) {
        requireManageOrg(callerPersonId);

        throw new NotImplementedException("권한 그룹 수정 (E5-2·E5-3)");
    }

    @Override
    public void delete(long callerPersonId, long groupId) {
        requireManageOrg(callerPersonId);

        throw new NotImplementedException("권한 그룹 삭제 (E5-3·E5-4)");
    }

    private void requireManageOrg(long callerPersonId) {
        if (!orgPermissionService.has(callerPersonId, OrgPermission.MANAGE_ORG)) {
            throw new ForbiddenException("사용자·조직 관리 권한이 없습니다");
        }
    }
}
