package kr.proten.pms.maintenance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.StaleVersionException;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.repository.MaintenanceContactRepository;
import kr.proten.pms.maintenance.repository.MaintenanceContractRepository;
import kr.proten.pms.maintenance.repository.MaintenanceSiteRepository;
import kr.proten.pms.maintenance.service.MaintenanceQueryService;
import kr.proten.pms.maintenance.service.dto.ContactCommand;
import kr.proten.pms.maintenance.service.dto.ContractCommand;
import kr.proten.pms.maintenance.service.dto.ContractDetail;
import kr.proten.pms.maintenance.service.dto.SiteCommand;
import kr.proten.pms.maintenance.service.dto.SiteView;
import kr.proten.pms.maintenance.service.entity.ContactDetails;
import kr.proten.pms.maintenance.service.entity.ContactParty;
import kr.proten.pms.maintenance.service.entity.ContractProfile;
import kr.proten.pms.maintenance.service.entity.ContractStatus;
import kr.proten.pms.maintenance.service.entity.MaintenanceContact;
import kr.proten.pms.maintenance.service.entity.MaintenanceContract;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import kr.proten.pms.maintenance.service.entity.SiteChannel;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.PersonDirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 유지보수 계약·사이트 쓰기 (AC D2-1·D2-2·D2-4).
 *
 * 권한 판정(403)은 {@link MaintenanceWriteAuthorizationTest}가 네 경로를 한 목록에서
 * 잠그므로 여기서 반복하지 않는다 — 이 테스트는 판정을 통과한 뒤의 규칙만 본다
 * ({@code GradeCommandTest} 선례).
 */
@ExtendWith(MockitoExtension.class)
class ContractCommandTest {
    private static final long MANAGER_ID = 7L;
    private static final long CONTRACT_ID = 101L;
    private static final long SITE_ID = 55L;
    private static final long ENGINEER_ID = 26L;

    @Mock
    private MaintenanceContractRepository contractRepository;
    @Mock
    private MaintenanceSiteRepository siteRepository;
    @Mock
    private MaintenanceContactRepository contactRepository;
    @Mock
    private MaintenanceQueryService queryService;
    @Mock
    private MaintenanceViewFactory viewFactory;
    @Mock
    private PersonDirectoryService personDirectoryService;
    @Mock
    private MaintenanceAuditRecorder auditRecorder;
    @Mock
    private OrgPermissionService orgPermissionService;

    private ContractCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(orgPermissionService.has(MANAGER_ID, OrgPermission.MANAGE_CONTRACTS))
                .thenReturn(true);
        lenient().when(personDirectoryService.existsActive(anyLong())).thenReturn(true);
        lenient().when(contractRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // 수정 경로는 saveAndFlush로 저장한다 — 응답의 version이 커밋 뒤 값이어야 하기 때문이고,
        // 그 이유는 통합 테스트가 실측으로 잡는다(단위에서는 반환만 이어 준다)
        lenient().when(contractRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(siteRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // 사이트 id는 DB가 만든다 — 저장 뒤에야 연락처가 붙을 수 있으므로 단위
        // 테스트에서는 주입해 준다(`ProjectFixtures`와 같은 이유)
        lenient().when(siteRepository.save(any())).thenAnswer(invocation -> {
            MaintenanceSite site = invocation.getArgument(0);
            ReflectionTestUtils.setField(site, "id", SITE_ID);

            return site;
        });
        service = new ContractCommandServiceImpl(
                contractRepository,
                siteRepository,
                contactRepository,
                queryService,
                viewFactory,
                personDirectoryService,
                new ContractWriteGuard(orgPermissionService),
                auditRecorder,
                new ContactAssembler());
    }

    @Test
    @DisplayName("D2-1 — 직접 등록한 계약은 max(id)+1을 받고 원천 프로젝트가 없다")
    void createTakesNextIdAndHasNoSourceProject() {
        // Given
        when(contractRepository.nextId()).thenReturn(106L);
        when(queryService.getContract(106L)).thenReturn(detail());

        // When
        service.create(MANAGER_ID, command());

        // Then
        ArgumentCaptor<MaintenanceContract> saved =
                ArgumentCaptor.forClass(MaintenanceContract.class);
        verify(contractRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(106L);
        assertThat(saved.getValue().getSourceProjectId()).isNull();
        verify(auditRecorder).contractCreated(eq(MANAGER_ID), any());
    }

    @Test
    @DisplayName("D2-1 — 계약사·계약명·상태는 필수다 (표의 not null이 곧 규칙)")
    void requiredFieldsAreRejectedWhenMissing() {
        // Given
        ContractCommand blankContractor = with(c -> new ContractCommand(" ", c.name(), c.status(),
                null, null, null, null, null, null, null, null, null, null));
        ContractCommand noName = with(c -> new ContractCommand(c.contractor(), null, c.status(),
                null, null, null, null, null, null, null, null, null, null));
        ContractCommand noStatus = with(c -> new ContractCommand(c.contractor(), c.name(), null,
                null, null, null, null, null, null, null, null, null, null));

        // When · Then
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.create(MANAGER_ID, blankContractor));
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.create(MANAGER_ID, noName));
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.create(MANAGER_ID, noStatus));
        verify(contractRepository, never()).save(any());
    }

    @Test
    @DisplayName("D2-1 — 영업대표가 없는 인원이면 422 REF_NOT_FOUND")
    void unknownSalesRepIsUnprocessable() {
        // Given
        when(personDirectoryService.existsActive(999L)).thenReturn(false);
        ContractCommand unknownRep = with(c -> new ContractCommand(c.contractor(), c.name(),
                c.status(), null, null, null, null, null, 999L, null, null, null, null));

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.create(MANAGER_ID, unknownRep))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.REF_NOT_FOUND));
        verify(contractRepository, never()).save(any());
    }

    @Test
    @DisplayName("D2-2 — 수정은 바꾸기 직전 스냅샷을 떠서 바뀐 필드만 남긴다")
    void updateRecordsDiffFromSnapshotTakenBeforeTheChange() {
        // Given
        MaintenanceContract contract = contract();
        when(contractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
        when(queryService.getContract(CONTRACT_ID)).thenReturn(detail());
        ContractCommand renamed = with(c -> new ContractCommand(c.contractor(), "그룹웨어 유지보수(연장)",
                ContractStatus.ENDED, null, null, null, null, null, null, null, null, null, null));

        // When
        service.update(MANAGER_ID, CONTRACT_ID, renamed, 0L);

        // Then
        verify(auditRecorder).snapshot(contract);
        verify(auditRecorder).contractChanged(eq(MANAGER_ID), eq(contract), any());
        assertThat(contract.getName()).isEqualTo("그룹웨어 유지보수(연장)");
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.ENDED);
    }

    @Test
    @DisplayName("D2-2 — 수정은 시트 유래 필드를 건드리지 않는다 (원문 보존)")
    void updateKeepsSheetOriginFields() {
        // Given
        MaintenanceContract contract = contract();
        when(contractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
        when(queryService.getContract(CONTRACT_ID)).thenReturn(detail());

        // When
        service.update(MANAGER_ID, CONTRACT_ID, command(), 0L);

        // Then
        assertThat(contract.getSheetSection()).isEqualTo("2026 계약");
        assertThat(contract.getContractDateNote()).isEqualTo("자동연장");
        assertThat(contract.getSourceProjectId()).isNull();
    }

    @Test
    @DisplayName("D2-2 — version이 어긋나면 409 STALE_VERSION이고 아무것도 바뀌지 않는다")
    void updateRejectsStaleVersion() {
        // Given
        MaintenanceContract contract = contract();
        when(contractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));

        // When · Then
        assertThatExceptionOfType(StaleVersionException.class)
                .isThrownBy(() -> service.update(MANAGER_ID, CONTRACT_ID, command(), 9L));
        assertThat(contract.getName()).isEqualTo("그룹웨어 유지보수");
        verify(auditRecorder, never()).contractChanged(anyLong(), any(), any());
    }

    @Test
    @DisplayName("없는 계약을 수정하면 404")
    void updatingMissingContractIsNotFound() {
        // Given
        when(contractRepository.findById(CONTRACT_ID)).thenReturn(Optional.empty());

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.update(MANAGER_ID, CONTRACT_ID, command(), 0L));
    }

    @Test
    @DisplayName("D2-4 — 사이트를 추가하면 연락처가 함께 저장되고 raw가 조립된다")
    void addSiteStoresAssembledContacts() {
        // Given
        when(contractRepository.existsById(CONTRACT_ID)).thenReturn(true);
        when(viewFactory.toSiteViews(any(), any())).thenReturn(List.of(siteView()));
        SiteCommand withContact = siteCommand(List.of(new ContactCommand(ContactParty.CLIENT,
                "이준혁", "사원", "02-2140-5773", "wnsgur0718@kaoni.com")));

        // When
        service.addSite(MANAGER_ID, CONTRACT_ID, withContact);

        // Then
        ArgumentCaptor<List<MaintenanceContact>> contacts = ArgumentCaptor.captor();
        verify(contactRepository).saveAll(contacts.capture());
        assertThat(contacts.getValue()).singleElement().satisfies(contact -> {
            assertThat(contact.getName()).isEqualTo("이준혁");
            assertThat(contact.getRaw())
                    .isEqualTo("이준혁 사원 02-2140-5773 (wnsgur0718@kaoni.com)");
        });
        verify(auditRecorder).siteCreated(eq(MANAGER_ID), any());
    }

    @Test
    @DisplayName("없는 계약에 사이트를 추가하면 404")
    void addSiteToMissingContractIsNotFound() {
        // Given
        when(contractRepository.existsById(CONTRACT_ID)).thenReturn(false);

        // When · Then
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.addSite(MANAGER_ID, CONTRACT_ID, siteCommand(List.of())));
        verify(siteRepository, never()).save(any());
    }

    @Test
    @DisplayName("D2-4 — 담당 엔지니어가 없는 인원이면 422 REF_NOT_FOUND")
    void unknownEngineerIsUnprocessable() {
        // Given
        when(contractRepository.existsById(CONTRACT_ID)).thenReturn(true);
        when(personDirectoryService.existsActive(ENGINEER_ID)).thenReturn(false);

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> service.addSite(MANAGER_ID, CONTRACT_ID, siteCommand(List.of())))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.REF_NOT_FOUND));
        verify(siteRepository, never()).save(any());
    }

    @Test
    @DisplayName("D2-4 — 사이트 수정은 연락처를 전체 교체한다 (§7 PUT 의미론)")
    void updateSiteReplacesContacts() {
        // Given
        MaintenanceSite site = site();
        MaintenanceContact existing = MaintenanceContact.of(SITE_ID, ContactParty.CLIENT,
                new ContactDetails("김승윤", "차장", null, null), "김승윤 차장");
        when(siteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(contactRepository.findBySiteIdInOrderByIdAsc(List.of(SITE_ID)))
                .thenReturn(List.of(existing));
        when(viewFactory.toSiteViews(any(), any())).thenReturn(List.of(siteView()));
        SiteCommand replacement = siteCommand(List.of(new ContactCommand(ContactParty.CONTRACTOR,
                "박민수", null, "010-1111-2222", null)));

        // When
        service.updateSite(MANAGER_ID, SITE_ID, replacement, 0L);

        // Then
        verify(contactRepository).deleteAll(List.of(existing));
        ArgumentCaptor<List<MaintenanceContact>> saved = ArgumentCaptor.captor();
        verify(contactRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(contact ->
                assertThat(contact.getRaw()).isEqualTo("박민수 010-1111-2222"));
        verify(auditRecorder).siteChanged(eq(MANAGER_ID), eq(site), any());
    }

    @Test
    @DisplayName("D2-4 — 사이트 수정에서 version이 어긋나면 409")
    void updateSiteRejectsStaleVersion() {
        // Given
        MaintenanceSite site = site();
        when(siteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));

        // When · Then
        assertThatExceptionOfType(StaleVersionException.class)
                .isThrownBy(() ->
                        service.updateSite(MANAGER_ID, SITE_ID, siteCommand(List.of()), 4L));
        assertThat(site.getName()).isEqualTo("가천대길병원");
        verify(contactRepository, never()).saveAll(any());
    }

    private static ContractCommand command() {
        return new ContractCommand("㈜가온아이", "그룹웨어 유지보수", ContractStatus.ACTIVE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                61320000L, 5110000L, null, "검색엔진", "그룹웨어", null, null);
    }

    private static ContractCommand with(UnaryOperator<ContractCommand> edit) {
        return edit.apply(command());
    }

    private static MaintenanceContract contract() {
        return MaintenanceContract.of(new ContractProfile(CONTRACT_ID, null, "㈜가온아이",
                "그룹웨어 유지보수", ContractStatus.ACTIVE, "2026 계약", LocalDate.of(2026, 1, 1),
                "자동연장", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 61320000L,
                5110000L, null, "검색엔진", "그룹웨어", null, null));
    }

    private static SiteCommand siteCommand(List<ContactCommand> contacts) {
        return new SiteCommand("가천대길병원", SiteChannel.OEM, null, ENGINEER_ID, contacts);
    }

    private static MaintenanceSite site() {
        return MaintenanceSite.of(CONTRACT_ID, "가천대길병원", SiteChannel.OEM, null, ENGINEER_ID);
    }

    private static SiteView siteView() {
        return new SiteView(SITE_ID, "가천대길병원", "OEM", null, null, List.of(), 0L);
    }

    private static ContractDetail detail() {
        return new ContractDetail(CONTRACT_ID, null, "㈜가온아이", "그룹웨어 유지보수", "유지",
                "2026 계약", null, null, null, null, null, null, null, null, null, null, null,
                List.of(), Map.of(), 1L);
    }
}
