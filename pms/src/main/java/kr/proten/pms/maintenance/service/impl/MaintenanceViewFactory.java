package kr.proten.pms.maintenance.service.impl;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.proten.pms.maintenance.service.dto.ContactView;
import kr.proten.pms.maintenance.service.dto.ContractSummary;
import kr.proten.pms.maintenance.service.dto.SiteView;
import kr.proten.pms.maintenance.service.entity.MaintenanceContact;
import kr.proten.pms.maintenance.service.entity.MaintenanceContract;
import kr.proten.pms.maintenance.service.entity.MaintenanceSite;
import kr.proten.pms.maintenance.service.entity.SiteChannel;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.person.PersonRef;
import org.springframework.stereotype.Component;

/**
 * 엔티티 → 표현 변환 (계약·사이트·연락처).
 *
 * <p>변환을 한 곳에 모으는 이유는 project의 {@code ProjectViewFactory}와 같다:
 * 목록과 상세가 같은 사이트 표현을 쓰므로 이름·엔지니어 해석이 두 서비스에서
 * 갈라지면 화면이 자리마다 다르게 보인다.
 *
 * <p>인원 참조는 <b>한 번에 모아 받는다</b> — 사이트 45개마다 엔지니어를 물으면
 * N+1이 모듈 경계를 넘는다.
 */
@Component
class MaintenanceViewFactory {
    private final PersonDirectoryService personDirectoryService;

    MaintenanceViewFactory(PersonDirectoryService personDirectoryService) {
        this.personDirectoryService = personDirectoryService;
    }

    List<ContractSummary> toSummaries(
            List<MaintenanceContract> contracts,
            Map<Long, Integer> siteCountByContract,
            Map<Long, List<String>> matchedSiteNames) {
        return contracts.stream()
                .map(contract -> new ContractSummary(
                        contract.getId(),
                        contract.getContractor(),
                        contract.getName(),
                        contract.getStatus().label(),
                        contract.getStartDate(),
                        contract.getEndDate(),
                        siteCountByContract.getOrDefault(contract.getId(), 0),
                        matchedSiteNames.getOrDefault(contract.getId(), List.of())))
                .toList();
    }

    List<SiteView> toSiteViews(List<MaintenanceSite> sites, List<MaintenanceContact> contacts) {
        Map<Long, PersonRef> engineers = refsOf(sites.stream()
                .map(MaintenanceSite::getEngineerId)
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<Long, List<MaintenanceContact>> contactsBySite = contacts.stream()
                .collect(Collectors.groupingBy(MaintenanceContact::getSiteId));

        return sites.stream()
                .map(site -> new SiteView(
                        site.getId(),
                        site.getName(),
                        label(site.getChannel()),
                        site.getServerSpec(),
                        site.getEngineerId() == null ? null : engineers.get(site.getEngineerId()),
                        toContactViews(contactsBySite.getOrDefault(site.getId(), List.of())),
                        site.getVersion()))
                .toList();
    }

    /** 인원 참조를 id로 찾을 수 있게 모아 온다 — 부재 id는 결과에서 빠진다. */
    Map<Long, PersonRef> refsOf(Collection<Long> personIds) {
        Set<Long> distinct = new LinkedHashSet<>(personIds);

        if (distinct.isEmpty()) {
            return Map.of();
        }

        return personDirectoryService.findRefs(distinct).stream()
                .collect(Collectors.toMap(PersonRef::id, Function.identity()));
    }

    private List<ContactView> toContactViews(List<MaintenanceContact> contacts) {
        return contacts.stream()
                .map(contact -> new ContactView(
                        contact.getId(),
                        contact.getParty().label(),
                        contact.getName(),
                        contact.getTitle(),
                        contact.getPhone(),
                        contact.getEmail(),
                        contact.getRaw()))
                .toList();
    }

    private static String label(SiteChannel channel) {
        return channel == null ? null : channel.label();
    }
}
