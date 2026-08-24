package kr.proten.pms.maintenance.service.impl;

import java.util.List;
import java.util.Objects;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.MaintenanceHandedOver;
import kr.proten.pms.maintenance.repository.MaintenanceContractRepository;
import kr.proten.pms.maintenance.repository.MaintenanceSiteRepository;
import kr.proten.pms.maintenance.service.entity.ContractProfile;
import kr.proten.pms.maintenance.service.entity.ContractStatus;
import kr.proten.pms.maintenance.service.entity.MaintenanceContract;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.HandoverPort;
import kr.proten.pms.project.HandoverSpec;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code HandoverPort} 구현 — 이관 계약·사이트를 만든다 (AC D1-1).
 *
 * <p>이 클래스가 <b>maintenance → project 간선의 유일한 지점</b>이다: 포트 인터페이스와
 * 그 입력({@code HandoverSpec})만 import한다. 그 방향을 고른 근거는 포트 javadoc에 있다.
 *
 * <p><b>{@code ContractCommandService}를 부르지 않는다</b>(D2 쓰기 경로). 그쪽은 모든
 * 메서드가 {@code ContractWriteGuard}("계약 관리" 플래그)를 지나므로, 그 플래그가 없는
 * PM은 자기 프로젝트를 이관할 수 없게 된다 — D1은 `[PM]`이고 판정은 project가 이미
 * 끝냈다. 입구가 둘인 것은 D2-1이 이미 적어 둔 사실이고(이관과 직접 등록),
 * 이 클래스가 그 다른 하나다.
 *
 * <p>{@code @Transactional(propagation = REQUIRED)}이 기본값이므로 <b>호출자의
 * 트랜잭션에 참여한다</b> — 프로젝트 상태 전이가 롤백되면 계약도 남지 않는다(D1-2).
 * 새 트랜잭션을 열면 그 원자성이 깨지므로 여기에 {@code REQUIRES_NEW}를 두면 안 된다.
 */
@Component
@Transactional
class HandoverAdapter implements HandoverPort {
    private final MaintenanceContractRepository contractRepository;
    private final MaintenanceSiteRepository siteRepository;
    private final PersonDirectoryService personDirectoryService;
    private final MaintenanceAuditRecorder auditRecorder;
    private final ApplicationEventPublisher events;

    HandoverAdapter(
            MaintenanceContractRepository contractRepository,
            MaintenanceSiteRepository siteRepository,
            PersonDirectoryService personDirectoryService,
            MaintenanceAuditRecorder auditRecorder,
            ApplicationEventPublisher events) {
        this.contractRepository = contractRepository;
        this.siteRepository = siteRepository;
        this.personDirectoryService = personDirectoryService;
        this.auditRecorder = auditRecorder;
        this.events = events;
    }

    /**
     * 이관 계약과 사이트를 만든다 (AC D1-1).
     *
     * <p><b>검증이 저장보다 앞이다</b>(D1-3 — "상태 전이도 미발생"): 사이트 하나라도
     * 필수값이 비어 있으면 계약도 만들지 않는다. 사이트를 한 건씩 검증하며 저장하면
     * 세 번째에서 400이 나도 앞의 둘은 이미 들어가 있다 — 그 두 행은 호출자의
     * 트랜잭션이 롤백되며 사라지지만, 그것에 기대는 순서를 코드로 적어 두지 않는다.
     */
    @Override
    public void createHandoverContract(long callerPersonId, long projectId, HandoverSpec spec) {
        validate(spec);

        // 계약 id는 max(id)+1 — 유지보수 계약은 하드 삭제가 없는 칸이다(§4 표)
        MaintenanceContract contract = contractRepository.save(MaintenanceContract.of(
                new ContractProfile(contractRepository.nextId(), projectId,
                        spec.contractor().trim(), spec.name().trim(), ContractStatus.ACTIVE,
                        null, spec.startDate(), null, spec.startDate(), spec.endDate(),
                        spec.amount(), spec.monthlyAmount(), null, null, null, null, null)));
        auditRecorder.contractCreated(callerPersonId, contract);

        List<MaintenanceSite> sites = spec.sites().stream()
                .map(site -> MaintenanceSite.of(contract.getId(), site.name().trim(),
                        null, null, site.engineerId()))
                .toList();
        siteRepository.saveAll(sites);
        sites.forEach(site -> auditRecorder.siteCreated(callerPersonId, site));

        events.publishEvent(new MaintenanceHandedOver(projectId, contract.getId(),
                contract.getName(), callerPersonId, engineerIdsOf(spec)));
    }

    /**
     * §4 표의 {@code not null}이 곧 입력 규칙이다 — 계약사·계약명, 그리고 D1-1이 더한
     * <b>사이트 1개 이상 + 각 사이트의 담당 엔지니어</b>다.
     *
     * <p>계약 상태는 받지 않고 {@code 유지}(ACTIVE)로 고정한다: 이관된 계약이 처음부터
     * 종료 상태일 이유가 없고, D1-1의 필수 정보 목록에도 상태가 없다.
     *
     * <p><b>계약일은 시작일과 같은 값으로 둔다</b>: 이관 폼에 계약일 칸이 없고(D1-1의
     * 필수 정보 목록에 없다) 이관은 그날 결정되는 일이라 시작일이 곧 계약일이다.
     * 직접 등록(D2-1)은 두 칸을 따로 받는다 — 시트에서 온 계약은 계약일과 개시일이
     * 갈리는 경우가 실재하기 때문이고, 이관에는 그 사정이 없다.
     *
     * <p>기간에 "종료일 > 시작일"을 걸지 않는 것은 D2-1과 같은 이유다 — 시드에 기간
     * 역순 계약이 1건 실재하므로 AC에 없는 규칙을 구현이 지어내면 실 데이터를 거부한다.
     */
    private void validate(HandoverSpec spec) {
        required(spec.contractor(), "계약사는 필수입니다", "contractor");
        required(spec.name(), "계약명은 필수입니다", "name");

        if (spec.sites() == null || spec.sites().isEmpty()) {
            throw new ValidationException("사이트를 1개 이상 등록해야 합니다", "sites");
        }

        for (HandoverSpec.Site site : spec.sites()) {
            required(site.name(), "사이트명은 필수입니다", "sites.name");

            if (site.engineerId() == null) {
                throw new ValidationException(
                        "사이트마다 담당 엔지니어가 필요합니다", "sites.engineerId");
            }

            if (!personDirectoryService.existsActive(site.engineerId())) {
                throw new UnprocessableException(ErrorCode.REF_NOT_FOUND,
                        "존재하지 않는 인원입니다 — sites.engineerId");
            }
        }
    }

    private static List<Long> engineerIdsOf(HandoverSpec spec) {
        return spec.sites().stream()
                .map(HandoverSpec.Site::engineerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static void required(String value, String message, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message, field);
        }
    }
}
