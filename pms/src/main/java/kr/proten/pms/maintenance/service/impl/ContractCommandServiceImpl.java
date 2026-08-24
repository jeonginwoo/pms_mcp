package kr.proten.pms.maintenance.service.impl;

import java.util.List;
import java.util.Map;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.repository.MaintenanceContactRepository;
import kr.proten.pms.maintenance.repository.MaintenanceContractRepository;
import kr.proten.pms.maintenance.repository.MaintenanceSiteRepository;
import kr.proten.pms.maintenance.service.ContractCommandService;
import kr.proten.pms.maintenance.service.MaintenanceQueryService;
import kr.proten.pms.maintenance.service.dto.ContractCommand;
import kr.proten.pms.maintenance.service.dto.ContractDetail;
import kr.proten.pms.maintenance.service.dto.SiteCommand;
import kr.proten.pms.maintenance.service.dto.SiteView;
import kr.proten.pms.maintenance.service.entity.ContractEdit;
import kr.proten.pms.maintenance.service.entity.ContractProfile;
import kr.proten.pms.maintenance.service.entity.MaintenanceContact;
import kr.proten.pms.maintenance.service.entity.MaintenanceContract;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import kr.proten.pms.person.PersonDirectoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유지보수 계약·사이트 쓰기 (US-D2).
 *
 * 네 유스케이스가 같은 순서를 밟는다 — <b>권한(403) → 존재(404) → 낙관적 락(409) →
 * 입력·참조(400·422) → 변경 → 감사</b>. 권한이 맨 앞인 것은 EPIC E와 같은 이유이고
 * (판정보다 조회가 앞서면 404·422가 403을 가린다), 락이 입력 검사보다 앞인 것은
 * "다른 사람이 이미 바꿨다"가 내 입력이 옳은지와 무관한 사실이기 때문이다.
 *
 * 계약 id는 {@code max(id)+1}이다 — 참조 데이터 id 발급 규칙(PRD-pms §4 표)에서
 * 유지보수 계약은 <b>하드 삭제가 없는</b> 칸이다(D2-2 — 종료는 상태로 표현한다).
 * 삭제가 없으면 id가 회수되지 않으므로 시퀀스까지 필요하지 않다.
 *
 * 응답은 조회 계약을 그대로 부른다 — 상세의 모양(사이트·연락처·이슈 요약·영업대표
 * 참조)을 여기서 다시 조립하면 정본이 두 벌이 되고, 화면이 등록 직후와 새로고침 후에
 * 다른 것을 보게 된다.
 */
@Service
@Transactional
class ContractCommandServiceImpl implements ContractCommandService {
    private final MaintenanceContractRepository contractRepository;
    private final MaintenanceSiteRepository siteRepository;
    private final MaintenanceContactRepository contactRepository;
    private final MaintenanceQueryService queryService;
    private final MaintenanceViewFactory viewFactory;
    private final PersonDirectoryService personDirectoryService;
    private final ContractWriteGuard writeGuard;
    private final MaintenanceAuditRecorder auditRecorder;
    private final ContactAssembler contactAssembler;

    ContractCommandServiceImpl(
            MaintenanceContractRepository contractRepository,
            MaintenanceSiteRepository siteRepository,
            MaintenanceContactRepository contactRepository,
            MaintenanceQueryService queryService,
            MaintenanceViewFactory viewFactory,
            PersonDirectoryService personDirectoryService,
            ContractWriteGuard writeGuard,
            MaintenanceAuditRecorder auditRecorder,
            ContactAssembler contactAssembler) {
        this.contractRepository = contractRepository;
        this.siteRepository = siteRepository;
        this.contactRepository = contactRepository;
        this.queryService = queryService;
        this.viewFactory = viewFactory;
        this.personDirectoryService = personDirectoryService;
        this.writeGuard = writeGuard;
        this.auditRecorder = auditRecorder;
        this.contactAssembler = contactAssembler;
    }

    @Override
    public ContractDetail create(long callerPersonId, ContractCommand command) {
        writeGuard.require(callerPersonId);

        ContractEdit edit = validated(command);
        long id = contractRepository.nextId();
        // sourceProjectId·시트 유래 두 칸은 비운다 — 직접 등록에는 원천 프로젝트도
        // 시트 원문도 없다(이관 D1이 채우는 자리다)
        MaintenanceContract contract = contractRepository.save(
                MaintenanceContract.of(new ContractProfile(id, null, edit.contractor(),
                        edit.name(), edit.status(), null, edit.contractDate(), null,
                        edit.startDate(), edit.endDate(), edit.amount(), edit.monthlyAmount(),
                        edit.salesRepId(), edit.category(), edit.targetInfra(),
                        edit.regularCheck(), edit.note())));
        auditRecorder.contractCreated(callerPersonId, contract);

        return queryService.getContract(contract.getId());
    }

    @Override
    public ContractDetail update(
            long callerPersonId, long contractId, ContractCommand command, long version) {
        writeGuard.require(callerPersonId);

        MaintenanceContract contract = contractRepository.findById(contractId)
                .orElseThrow(NotFoundException::new);
        contract.requireVersion(version);
        ContractEdit edit = validated(command);
        // 바꾸기 직전에 떠 둔다 — 바뀐 필드만 이력에 남는다
        Map<String, Object> before = auditRecorder.snapshot(contract);
        contract.update(edit);
        // flush 해야 응답의 version이 커밋 뒤의 값이 된다 — 안 하면 0을 돌려주고 그
        // 값으로 다시 수정하려는 호출자가 409를 받는다(project가 같은 이유로 saveAndFlush)
        MaintenanceContract saved = contractRepository.saveAndFlush(contract);
        auditRecorder.contractChanged(callerPersonId, saved, before);

        return queryService.getContract(contractId);
    }

    @Override
    public SiteView addSite(long callerPersonId, long contractId, SiteCommand command) {
        writeGuard.require(callerPersonId);

        if (!contractRepository.existsById(contractId)) {
            throw new NotFoundException();
        }

        validate(command);
        MaintenanceSite site = siteRepository.save(MaintenanceSite.of(contractId,
                command.name().trim(), command.channel(), command.serverSpec(),
                command.engineerId()));
        auditRecorder.siteCreated(callerPersonId, site);

        return viewOf(site, store(site.getId(), command));
    }

    @Override
    public SiteView updateSite(
            long callerPersonId, long siteId, SiteCommand command, long version) {
        writeGuard.require(callerPersonId);

        MaintenanceSite site = siteRepository.findById(siteId)
                .orElseThrow(NotFoundException::new);
        site.requireVersion(version);
        validate(command);
        // 사이트를 바꾸기 **전에** 읽는다 — 더러워진 세션에 질의하면 JPA가 먼저 flush 해
        // version이 한 유스케이스에서 두 번 오른다(conventions §4)
        List<MaintenanceContact> existing =
                contactRepository.findBySiteIdInOrderByIdAsc(List.of(siteId));
        Map<String, Object> before = auditRecorder.snapshot(site);
        site.update(command.name().trim(), command.channel(), command.serverSpec(),
                command.engineerId());
        // 계약 수정과 같은 이유로 flush 한다 — 응답의 version이 다음 수정의 입력이다
        MaintenanceSite saved = siteRepository.saveAndFlush(site);
        auditRecorder.siteChanged(callerPersonId, saved, before);
        // 통째로 갈아 끼운다 (§7 PUT 의미론 — SiteCommand 주석)
        contactRepository.deleteAll(existing);

        return viewOf(saved, store(siteId, command));
    }

    /** 요청의 연락처를 저장한다 — 조립은 {@link ContactAssembler}가 한다. */
    private List<MaintenanceContact> store(long siteId, SiteCommand command) {
        List<MaintenanceContact> contacts = command.contacts().stream()
                .map(contact -> contactAssembler.toContact(siteId, contact))
                .toList();
        contactRepository.saveAll(contacts);

        return contacts;
    }

    private SiteView viewOf(MaintenanceSite site, List<MaintenanceContact> contacts) {
        return viewFactory.toSiteViews(List.of(site), contacts).getFirst();
    }

    /**
     * 표의 {@code not null}이 곧 입력 규칙이다 — 계약사·계약명·상태 셋뿐이고
     * 나머지는 시드에도 빈 칸이 흔하다. 기간·금액에 규칙을 더 걸지 않는 것은
     * 실측 때문이다: 시드 105건 중 종료일이 시작일보다 이른 계약이 1건 있다.
     * AC에 없는 규칙을 구현이 지어내면 실 데이터의 모양을 거부하게 된다.
     */
    private ContractEdit validated(ContractCommand command) {
        ContractEdit edit = new ContractEdit(
                required(command.contractor(), "계약사는 필수입니다", "contractor"),
                required(command.name(), "계약명은 필수입니다", "name"),
                command.status(),
                command.contractDate(),
                command.startDate(),
                command.endDate(),
                command.amount(),
                command.monthlyAmount(),
                command.salesRepId(),
                command.category(),
                command.targetInfra(),
                command.regularCheck(),
                command.note());

        if (edit.status() == null) {
            throw new ValidationException("계약 상태는 필수입니다", "status");
        }

        requirePerson(edit.salesRepId(), "salesRepId");

        return edit;
    }

    private void validate(SiteCommand command) {
        required(command.name(), "사이트명은 필수입니다", "name");
        requirePerson(command.engineerId(), "engineerId");
    }

    /** 참조 검증 — 미지정(null)은 정상이고, 지정했는데 없는 인원이면 422다(A1-3과 같은 규칙). */
    private void requirePerson(Long personId, String field) {
        if (personId == null) {
            return;
        }

        if (!personDirectoryService.existsActive(personId)) {
            throw new UnprocessableException(ErrorCode.REF_NOT_FOUND,
                    "존재하지 않는 인원입니다 — " + field);
        }
    }

    private static String required(String value, String message, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message, field);
        }

        return value.trim();
    }
}
