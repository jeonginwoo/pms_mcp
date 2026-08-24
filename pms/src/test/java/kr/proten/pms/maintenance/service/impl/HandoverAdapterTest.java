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
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.UnprocessableException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.MaintenanceHandedOver;
import kr.proten.pms.maintenance.repository.MaintenanceContractRepository;
import kr.proten.pms.maintenance.repository.MaintenanceSiteRepository;
import kr.proten.pms.maintenance.service.entity.ContractStatus;
import kr.proten.pms.maintenance.service.entity.MaintenanceContract;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.project.HandoverSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 이관 계약 생성 — {@code HandoverPort} 구현 (AC D1-1·D1-3).
 *
 * <p>권한 판정이 없는 것이 전제다: D1은 `[PM]`이고 그 판정은 project가 이미 끝냈다.
 * 이 클래스가 {@code ContractWriteGuard}를 지나지 않는다는 사실은 생성자에 그것이
 * <b>주입되지 않는다</b>는 것으로 드러난다.
 *
 * <p>시연 앵커는 명화공업이다(부록 B).
 */
@ExtendWith(MockitoExtension.class)
class HandoverAdapterTest {
    private static final long CALLER_ID = 13L;
    private static final long PROJECT_ID = 7L;
    private static final long CONTRACT_ID = 106L;
    private static final long ENGINEER_ID = 26L;
    private static final long OTHER_ENGINEER_ID = 31L;

    @Mock
    private MaintenanceContractRepository contractRepository;
    @Mock
    private MaintenanceSiteRepository siteRepository;
    @Mock
    private PersonDirectoryService personDirectoryService;
    @Mock
    private MaintenanceAuditRecorder auditRecorder;
    @Mock
    private ApplicationEventPublisher events;

    private HandoverAdapter adapter;

    @BeforeEach
    void setUp() {
        lenient().when(personDirectoryService.existsActive(anyLong())).thenReturn(true);
        lenient().when(contractRepository.nextId()).thenReturn(CONTRACT_ID);
        lenient().when(contractRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        adapter = new HandoverAdapter(contractRepository, siteRepository,
                personDirectoryService, auditRecorder, events);
    }

    @Test
    @DisplayName("D1-1 — 이관 계약은 sourceProjectId를 갖고 유지 상태로 시작한다")
    void createsContractLinkedToTheSourceProject() {
        // When
        adapter.createHandoverContract(CALLER_ID, PROJECT_ID, spec());

        // Then
        MaintenanceContract saved = captureContract();
        assertThat(saved.getId()).isEqualTo(CONTRACT_ID);
        // 직접 등록(D2-1)은 sourceProjectId가 null이다 — 이관이 그 칸을 채우는 유일한 입구다
        assertThat(saved.getSourceProjectId()).isEqualTo(PROJECT_ID);
        assertThat(saved.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(saved.getContractor()).isEqualTo("명화공업");
        assertThat(saved.getName()).isEqualTo("MES 유지보수");
        verify(auditRecorder).contractCreated(eq(CALLER_ID), any());
    }

    @Test
    @DisplayName("D1-1 — 사이트를 담당 엔지니어와 함께 만든다")
    void createsEverySiteWithItsEngineer() {
        // When
        adapter.createHandoverContract(CALLER_ID, PROJECT_ID, twoSiteSpec());

        // Then
        ArgumentCaptor<List<MaintenanceSite>> saved = ArgumentCaptor.captor();
        verify(siteRepository).saveAll(saved.capture());
        assertThat(saved.getValue())
                .extracting(MaintenanceSite::getName, MaintenanceSite::getEngineerId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("명화공업 본사", ENGINEER_ID),
                        org.assertj.core.groups.Tuple.tuple("명화공업 2공장", OTHER_ENGINEER_ID));
    }

    @Test
    @DisplayName("D1-3 — 사이트가 없으면 400이고 계약도 만들지 않는다")
    void rejectsSpecWithoutSites() {
        // Given
        HandoverSpec noSites = new HandoverSpec("명화공업", "MES 유지보수", null, null,
                null, null, List.of());

        // When · Then
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> adapter.createHandoverContract(CALLER_ID, PROJECT_ID, noSites))
                .satisfies(thrown -> assertThat(thrown.field()).isEqualTo("sites"));
        // 검증이 저장보다 앞이라는 것이 이 단정이다
        verify(contractRepository, never()).save(any());
        verify(siteRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("D1-3 — 담당 엔지니어 없는 사이트는 400이고 계약도 만들지 않는다")
    void rejectsSiteWithoutEngineer() {
        // Given — 담당 없이 이관하면 그 사이트의 이슈가 영원히 미배정으로 남는다(D3-1)
        HandoverSpec noEngineer = new HandoverSpec("명화공업", "MES 유지보수", null, null,
                null, null, List.of(new HandoverSpec.Site("명화공업 본사", null)));

        // When · Then
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() ->
                        adapter.createHandoverContract(CALLER_ID, PROJECT_ID, noEngineer))
                .satisfies(thrown ->
                        assertThat(thrown.field()).isEqualTo("sites.engineerId"));
        verify(contractRepository, never()).save(any());
    }

    @Test
    @DisplayName("D1-3 — 계약사·계약명이 비면 400이다 (§4 표의 not null)")
    void rejectsMissingRequiredContractFields() {
        // Given
        HandoverSpec noContractor = new HandoverSpec(" ", "MES 유지보수", null, null, null,
                null, List.of(new HandoverSpec.Site("명화공업 본사", ENGINEER_ID)));
        HandoverSpec noName = new HandoverSpec("명화공업", null, null, null, null,
                null, List.of(new HandoverSpec.Site("명화공업 본사", ENGINEER_ID)));

        // When · Then
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
                adapter.createHandoverContract(CALLER_ID, PROJECT_ID, noContractor));
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
                adapter.createHandoverContract(CALLER_ID, PROJECT_ID, noName));
        verify(contractRepository, never()).save(any());
    }

    @Test
    @DisplayName("D1-3 — 없는 인원을 담당으로 주면 422 REF_NOT_FOUND")
    void rejectsUnknownEngineer() {
        // Given
        when(personDirectoryService.existsActive(999L)).thenReturn(false);
        HandoverSpec unknown = new HandoverSpec("명화공업", "MES 유지보수", null, null, null,
                null, List.of(new HandoverSpec.Site("명화공업 본사", 999L)));

        // When · Then
        assertThatExceptionOfType(UnprocessableException.class)
                .isThrownBy(() -> adapter.createHandoverContract(CALLER_ID, PROJECT_ID, unknown))
                .satisfies(thrown ->
                        assertThat(thrown.code()).isEqualTo(ErrorCode.REF_NOT_FOUND));
        verify(contractRepository, never()).save(any());
    }

    @Test
    @DisplayName("D1-3 — 두 번째 사이트가 잘못되면 계약도 첫 사이트도 만들지 않는다")
    void validatesEverySiteBeforeSavingAnything() {
        // Given — 한 건씩 검증하며 저장하면 첫 사이트는 이미 들어가 있게 된다
        HandoverSpec secondBad = new HandoverSpec("명화공업", "MES 유지보수", null, null,
                null, null, List.of(
                        new HandoverSpec.Site("명화공업 본사", ENGINEER_ID),
                        new HandoverSpec.Site("명화공업 2공장", null)));

        // When · Then
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
                adapter.createHandoverContract(CALLER_ID, PROJECT_ID, secondBad));
        verify(contractRepository, never()).save(any());
        verify(siteRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("D1-1 — MaintenanceHandedOver를 발행한다 (담당자 중복은 제거된다)")
    void publishesHandedOverWithDistinctEngineers() {
        // Given — 두 사이트를 같은 사람이 담당하는 경우가 실무에 흔하다
        HandoverSpec sameEngineer = new HandoverSpec("명화공업", "MES 유지보수", null, null,
                null, null, List.of(
                        new HandoverSpec.Site("명화공업 본사", ENGINEER_ID),
                        new HandoverSpec.Site("명화공업 2공장", ENGINEER_ID)));

        // When
        adapter.createHandoverContract(CALLER_ID, PROJECT_ID, sameEngineer);

        // Then
        ArgumentCaptor<MaintenanceHandedOver> event =
                ArgumentCaptor.forClass(MaintenanceHandedOver.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().projectId()).isEqualTo(PROJECT_ID);
        assertThat(event.getValue().contractId()).isEqualTo(CONTRACT_ID);
        assertThat(event.getValue().contractName()).isEqualTo("MES 유지보수");
        // 실행자는 수신자가 아니라 문구의 재료다
        assertThat(event.getValue().handedOverBy()).isEqualTo(CALLER_ID);
        assertThat(event.getValue().siteEngineerIds()).containsExactly(ENGINEER_ID);
    }

    private MaintenanceContract captureContract() {
        ArgumentCaptor<MaintenanceContract> saved =
                ArgumentCaptor.forClass(MaintenanceContract.class);
        verify(contractRepository).save(saved.capture());

        return saved.getValue();
    }

    private static HandoverSpec spec() {
        return new HandoverSpec("명화공업", "MES 유지보수", LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31), 24000000L, 2000000L,
                List.of(new HandoverSpec.Site("명화공업 본사", ENGINEER_ID)));
    }

    private static HandoverSpec twoSiteSpec() {
        return new HandoverSpec("명화공업", "MES 유지보수", null, null, null, null, List.of(
                new HandoverSpec.Site("명화공업 본사", ENGINEER_ID),
                new HandoverSpec.Site("명화공업 2공장", OTHER_ENGINEER_ID)));
    }
}
