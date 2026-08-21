package kr.proten.pms.person.service.impl;

import java.util.Comparator;
import java.util.List;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.PermissionGroupRepository;
import kr.proten.pms.person.service.OrgPermissionService;
import kr.proten.pms.person.service.ReferenceQueryService;
import kr.proten.pms.person.service.dto.OrgPermission;
import kr.proten.pms.person.service.dto.ReferenceItem;
import kr.proten.pms.person.service.entity.Grade;
import kr.proten.pms.person.service.entity.PermissionGroup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 직급·권한 그룹 선택 목록 — 관리 화면 전용이라 조회도 관리 플래그를 요구한다.
 * 두 목록을 한 서비스에 두는 이유: 쓰임이 하나(등록 폼의 선택지)이고 규칙도 같다.
 */
@Service
@Transactional(readOnly = true)
public class ReferenceQueryServiceImpl implements ReferenceQueryService {
    private final GradeRepository gradeRepository;
    private final PermissionGroupRepository permissionGroupRepository;
    private final OrgPermissionService orgPermissionService;

    public ReferenceQueryServiceImpl(
            GradeRepository gradeRepository,
            PermissionGroupRepository permissionGroupRepository,
            OrgPermissionService orgPermissionService) {
        this.gradeRepository = gradeRepository;
        this.permissionGroupRepository = permissionGroupRepository;
        this.orgPermissionService = orgPermissionService;
    }

    public List<ReferenceItem> grades(long callerPersonId) {
        requireManageOrg(callerPersonId);

        return gradeRepository.findAll().stream()
                .sorted(Comparator.comparing(Grade::getId))
                .map(grade -> new ReferenceItem(grade.getId(), grade.getName()))
                .toList();
    }

    public List<ReferenceItem> permissionGroups(long callerPersonId) {
        requireManageOrg(callerPersonId);

        return permissionGroupRepository.findAll().stream()
                .sorted(Comparator.comparing(PermissionGroup::getId))
                .map(group -> new ReferenceItem(group.getId(), group.getName()))
                .toList();
    }

    private void requireManageOrg(long callerPersonId) {
        if (!orgPermissionService.has(callerPersonId, OrgPermission.MANAGE_ORG)) {
            throw new ForbiddenException("사용자·조직 관리 권한이 없습니다");
        }
    }
}
