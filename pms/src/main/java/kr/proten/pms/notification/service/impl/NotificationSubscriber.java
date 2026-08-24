package kr.proten.pms.notification.service.impl;

import java.util.List;
import kr.proten.pms.notification.NotificationService;
import kr.proten.pms.notification.NotificationType;
import kr.proten.pms.notification.NotifyCommand;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.project.AssignmentChanged;
import kr.proten.pms.resource.OverbookingDetected;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 이벤트를 알림으로 옮긴다 — <b>적재 경로의 유일한 입구</b>다 (§8 · AC F1-1).
 *
 * <p>다른 모듈이 {@code notify}를 부르지 않고 여기가 구독하는 이유는 순환이다:
 * resource는 이미 {@code OverbookingDetected}의 발행자라 이 클래스가 그 타입을
 * import하는데(구독자 → 발행자), resource가 거꾸로 {@code notify}를 부르면 반대 간선이
 * 함께 생긴다(2026-08-24 확정 — PRD-pms §3·§8).
 *
 * <p>문구를 여기서 만드는 것도 같은 이유다. 발행 측이 알림 문구를 만들면 "발행 측이
 * 구독자를 모른다"가 깨지고, 알림 문구를 고칠 때마다 남의 모듈을 열게 된다.
 */
@Component
class NotificationSubscriber {
    private final NotificationService notificationService;
    private final OrgPermissionService orgPermissionService;
    private final PersonDirectoryService personDirectoryService;

    NotificationSubscriber(
            NotificationService notificationService,
            OrgPermissionService orgPermissionService,
            PersonDirectoryService personDirectoryService) {
        this.notificationService = notificationService;
        this.orgPermissionService = orgPermissionService;
        this.personDirectoryService = personDirectoryService;
    }

    /**
     * 과부하 → 같은 조직의 관리자급에게 (AC F1-1).
     *
     * <p>"팀장"을 이름이나 가시성 scope로 찾지 않는다 — 권한 그룹은 사용자가 개명·삭제할
     * 수 있는 데이터이고(E5), 팀원도 2026-08-22부터 scope가 TEAM이라 구분이 안 된다.
     * 안정된 표식은 <b>"프로젝트 생성" 플래그</b>이고, 상위 PRD §4-3이 그것을
     * 관리자·부문장·팀장에게만 주도록 이미 선을 그어 뒀다(2026-08-24 사용자 결정).
     *
     * <p>멱등 키에 <b>월</b>을 넣는다: 같은 달의 과부하는 배정을 몇 번 고쳐도 한 번만
     * 알린다(F1-2). 달이 바뀌면 다른 사건이다.
     */
    @ApplicationModuleListener
    void onOverbookingDetected(OverbookingDetected event) {
        List<Long> recipients = orgPermissionService.findColleaguesWith(
                event.personId(), OrgPermission.CREATE_PROJECT);

        if (recipients.isEmpty()) {
            return;
        }

        String name = nameOf(event.personId());
        String message = "%s님이 %s에 과부하입니다 (기본 가동률 %d%%)"
                .formatted(name, event.month(), Math.round(event.basicPct()));
        String dedupeKey = "overbooked:%d:%s".formatted(event.personId(), event.month());

        recipients.forEach(recipient -> notificationService.notify(new NotifyCommand(
                recipient, NotificationType.OVERBOOKED, "Person", event.personId(),
                message, dedupeKey)));
    }

    /**
     * 배정됨 → 배정된 본인에게 (§8 {@code MemberAssignedToProject}).
     *
     * <p>수정·종료는 알리지 않는다: §8이 그 둘의 구독자를 resource로만 적었고, M/M을
     * 고칠 때마다 알림이 가면 배정 화면을 만지는 PM이 상대에게 소음을 보낸다.
     */
    @ApplicationModuleListener
    void onAssignmentChanged(AssignmentChanged event) {
        if (event.kind() != AssignmentChanged.Kind.ASSIGNED) {
            return;
        }

        notificationService.notify(new NotifyCommand(
                event.personId(),
                NotificationType.ASSIGNED,
                "Project",
                event.projectId(),
                "%s 프로젝트에 배정되었습니다".formatted(event.projectName()),
                "assigned:%d:%d".formatted(event.projectId(), event.personId())));
    }

    /** 이름을 못 찾으면 알림을 포기하지 않는다 — 문구만 덜 친절해진다. */
    private String nameOf(long personId) {
        return personDirectoryService.findRefs(List.of(personId)).stream()
                .map(PersonRef::name)
                .findFirst()
                .orElse("#" + personId);
    }
}
