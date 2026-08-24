package kr.proten.pms.maintenance.service.impl;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.lenient;

import java.util.List;
import kr.proten.pms.common.exception.ForbiddenException;
import kr.proten.pms.maintenance.service.dto.ContractCommand;
import kr.proten.pms.maintenance.service.dto.SiteCommand;
import kr.proten.pms.maintenance.service.entity.ContractStatus;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 유지보수 쓰기 네 경로의 권한 판정 (AC D2-3).
 *
 * 계약은 프로젝트 밖 행위라 프로젝트 역할이 아니라 <b>그룹 플래그</b>가 판정한다
 * (상위 PRD §4-3 — "계약 관리": 관리자·부문장·팀장). 조회는 전사 공개이므로(D4-3)
 * 이 모듈에서 화자가 판정에 쓰이는 곳은 쓰기뿐이고, 그래서 판정이 빠지면 아무
 * 조회 테스트도 깨지지 않는다 — EPIC E에서 조직 개명 골격이 그렇게 빠져 있었다
 * (2026-08-22 리뷰 발견). 네 경로를 한 목록에 모아 두는 이유가 그것이고,
 * 쓰기 경로가 늘면 여기에도 한 줄 늘어난다.
 *
 * 협력자는 전부 null이다({@code ScaffoldAuthorizationTest} 선례) — 판정에서 막히면
 * 그 뒤로 한 줄도 가지 않는다는 것이 검증 대상이고, null이 그 증명을 대신한다
 * (판정보다 저장소 조회가 앞서면 404·422가 403을 가리게 되고, 그때 NPE로 깨진다).
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceWriteAuthorizationTest {
    private static final long MANAGER_ID = 7L;
    private static final long MEMBER_ID = 28L;

    @Mock
    private OrgPermissionService orgPermissionService;

    private ContractCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(orgPermissionService.has(MEMBER_ID, OrgPermission.MANAGE_CONTRACTS))
                .thenReturn(false);
        service = new ContractCommandServiceImpl(null, null, null, null, null, null,
                new ContractWriteGuard(orgPermissionService), null, null);
    }

    @Test
    @DisplayName("D2-3 — 계약 관리 플래그가 없으면 계약 등록은 403")
    void createIsForbiddenWithoutTheFlag() {
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.create(MEMBER_ID, contractCommand()));
    }

    @Test
    @DisplayName("D2-3 — 계약 관리 플래그가 없으면 계약 수정은 403")
    void updateIsForbiddenWithoutTheFlag() {
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.update(MEMBER_ID, 101L, contractCommand(), 0L));
    }

    @Test
    @DisplayName("D2-3 — 계약 관리 플래그가 없으면 사이트 등록은 403")
    void addSiteIsForbiddenWithoutTheFlag() {
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.addSite(MEMBER_ID, 101L, siteCommand()));
    }

    @Test
    @DisplayName("D2-3 — 계약 관리 플래그가 없으면 사이트 수정은 403")
    void updateSiteIsForbiddenWithoutTheFlag() {
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.updateSite(MEMBER_ID, 55L, siteCommand(), 0L));
    }

    @Test
    @DisplayName("D2-3 — 플래그가 있으면 판정을 통과해 그 뒤 로직으로 간다")
    void theFlagLetsTheCallerThrough() {
        // Given
        lenient().when(orgPermissionService.has(MANAGER_ID, OrgPermission.MANAGE_CONTRACTS))
                .thenReturn(true);

        // When · Then — 협력자가 null이므로 판정을 지나면 NPE다. 403이 아니라는 것이 요점이다
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> service.create(MANAGER_ID, contractCommand()));
    }

    private static ContractCommand contractCommand() {
        return new ContractCommand("㈜가온아이", "그룹웨어 유지보수", ContractStatus.ACTIVE, null, null,
                null, null, null, null, null, null, null, null);
    }

    private static SiteCommand siteCommand() {
        return new SiteCommand("가천대길병원", null, null, null, List.of());
    }
}
