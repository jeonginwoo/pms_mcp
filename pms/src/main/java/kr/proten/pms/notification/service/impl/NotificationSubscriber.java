package kr.proten.pms.notification.service.impl;

import java.util.List;
import kr.proten.pms.maintenance.MaintenanceHandedOver;
import kr.proten.pms.maintenance.MaintenanceIssueRegistered;
import kr.proten.pms.notification.service.NotificationService;
import kr.proten.pms.notification.service.dto.NotifyCommand;
import kr.proten.pms.notification.service.entity.NotificationType;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.PersonDirectoryService;
import kr.proten.pms.person.PersonRef;
import kr.proten.pms.project.AssignmentChanged;
import kr.proten.pms.project.ProjectLifecycleChanged;
import kr.proten.pms.project.ProjectReminderDue;
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

    /**
     * 이슈 등록 → 그 이슈의 담당자에게 (AC D3-1 · §8 {@code MaintenanceIssueRegistered}).
     *
     * <p><b>담당자가 없으면 조용히 끝난다</b> — 사이트에 담당 엔지니어가 없는 경우이고,
     * 그 이슈는 D3-4의 미배정 필터가 찾도록 남는다. 발행 측은 알릴 사람이 있는지
     * 모르는 채로 발행한다({@code MaintenanceIssueRegistered} 주석) — 그 판단이 이 줄이다.
     *
     * <p><b>재배정(D3-2)은 알리지 않는다</b>: §8에 그 이벤트가 없고 AC도 등록만 적었다.
     * {@code AssignmentChanged}에서 수정·종료를 알리지 않는 것과 같은 자리이며,
     * 파급이 있는 공백으로 등재했다(담당이 바뀐 사람은 화면을 봐야 알게 된다).
     */
    @ApplicationModuleListener
    void onIssueRegistered(MaintenanceIssueRegistered event) {
        if (event.assigneeId() == null) {
            return;
        }

        String where = event.siteName() == null ? "유지보수" : event.siteName();
        notificationService.notify(new NotifyCommand(
                event.assigneeId(),
                NotificationType.ISSUE_ASSIGNED,
                "MaintenanceIssue",
                event.issueId(),
                "%s 이슈 담당자로 지정되었습니다 — %s".formatted(where, event.title()),
                "issue-assigned:%d:%d".formatted(event.issueId(), event.assigneeId())));
    }


    /**
     * 이관 완료 → 이관된 사이트의 담당 엔지니어에게 (AC D1-1 · §8
     * {@code MaintenanceHandedOver}).
     *
     * <p><b>수신자가 §8에 없어서 정했다</b>(ASSUMPTION — PRD-pms §12 등재): 이관으로
     * 일이 달라지는 사람은 <b>사이트 담당 엔지니어</b>다. 이관을 실행한 PM에게는 보내지
     * 않는다 — 자기가 방금 한 일이다. 실행자를 문구에 싣는 것은 그것과 다른 일이고,
     * "누가 넘겼나"는 받는 사람이 알아야 하는 정보다.
     *
     * <p><b>유형은 {@code PROJECT_COMPLETED}를 재사용한다</b>(사용자 결정 2026-08-25):
     * 그 열거의 javadoc이 이미 "완료·<b>이관</b> 안내"이고, 사용자에게 완료와 이관은 한
     * 사건의 두 단계다. 유형을 늘리면 설정 화면(H1-4)의 칸이 함께 늘어난다.
     *
     * <p>멱등 키에 계약 id를 넣는다: 한 프로젝트는 한 번만 이관되지만(유지보수중에서는
     * 재개도 이관도 불가) 키는 그 사실에 기대지 않는다(F1-2).
     */
    @ApplicationModuleListener
    void onHandedOver(MaintenanceHandedOver event) {
        if (event.siteEngineerIds().isEmpty()) {
            return;
        }

        String message = "%s님이 %s 유지보수 담당으로 이관했습니다"
                .formatted(nameOf(event.handedOverBy()), event.contractName());

        event.siteEngineerIds().stream()
                // 이관한 사람이 자기 사이트의 담당이기도 하면 자기에게는 보내지 않는다
                .filter(engineerId -> engineerId != event.handedOverBy())
                .forEach(engineerId -> notificationService.notify(new NotifyCommand(
                        engineerId, NotificationType.PROJECT_COMPLETED, "Project",
                        event.projectId(), message,
                        "handed-over:%d:%d".formatted(event.contractId(), engineerId))));
    }


    /**
     * 마감 임박·완료 지연 → 프로젝트를 끌고 가는 사람에게 (AC F2-1 · F3-1).
     *
     * <p>발행자가 수신자 재료를 실어 보내지만(역할 판정은 project의 것이다) <b>보낼지</b>는
     * 여기가 정한다 — 설정 꺼짐 필터(F1-5)는 {@code notify} 안에 있다.
     *
     * <p><b>멱등 키가 두 종류로 갈린다</b>(F2-2·F3-2): 마감 임박은 <b>점검이 돈 날</b>을
     * 넣어 하루 한 번씩 다시 알리고(F2-2가 멱등 단위를 "같은 날 재실행"으로 적었다),
     * 완료 지연은 <b>도달일</b>을 넣어 한 사이클에 한 번만 알린다. 마감은 날마다
     * 임박해지는 사건이고 완료 지연은 같은 사건이 안 풀린 것이라, 매일 보내면 후자는
     * 소음이 된다. 재개하면 도달일이 비고 다시 100%가 될 때 새로 찍히므로 키가
     * 달라져 새 사이클이 된다(F3-2 문면 그대로다).
     *
     * <p>마감 키에 <b>종료일</b>을 쓰면 종료일당 평생 1건이 되어 "일일 점검"이
     * 성립하지 않는다 — 2026-08-25 리뷰가 잡은 실제 결함이다(주석은 매일이라고
     * 적혀 있었고 코드는 아니었다).
     */
    @ApplicationModuleListener
    void onReminderDue(ProjectReminderDue event) {
        boolean deadline = event.kind() == ProjectReminderDue.Kind.DEADLINE_NEAR;
        NotificationType type = deadline
                ? NotificationType.DEADLINE_NEAR
                : NotificationType.COMPLETION_OVERDUE;
        String message = deadline
                ? "%s 종료일이 %s입니다".formatted(event.projectName(), event.dueDate())
                : "%s이(가) 100%%인 채 %s부터 완료 처리되지 않았습니다"
                        .formatted(event.projectName(), event.dueDate());

        event.recipientIds().forEach(recipient -> notificationService.notify(new NotifyCommand(
                recipient, type, "Project", event.projectId(), message,
                "%s:%d:%s".formatted(deadline ? "deadline" : "overdue",
                        event.projectId(),
                        deadline ? event.runDate() : event.dueDate()))));
    }

    /**
     * 완료 안내 · 재개 시 회수 (§8 {@code ProjectCompleted}·{@code ProjectReopened} ·
     * AC F3-3).
     *
     * <p><b>이 메서드가 2026-08-25에 메운 것은 배선이다</b>: §8이 두 이벤트를 명세했고
     * {@code withdrawUnread}도 구현·테스트돼 있었지만 <b>실사용 호출자가 없어</b>
     * 재개해도 완료 지연 알림이 남아 있었다. 능력과 배선은 다른 것이다.
     *
     * <p><b>재개는 알림을 만들지 않는다</b> — 걷어내기만 한다. 읽은 알림은 남긴다:
     * 이미 본 사실을 없던 일로 만들지 않는다({@code withdrawUnread} 주석).
     */
    @ApplicationModuleListener
    void onLifecycleChanged(ProjectLifecycleChanged event) {
        if (event.kind() == ProjectLifecycleChanged.Kind.REOPENED) {
            notificationService.withdrawUnread(
                    "Project", event.projectId(), NotificationType.COMPLETION_OVERDUE);

            return;
        }

        String message = "%s이(가) 완료되었습니다".formatted(event.projectName());

        event.assigneeIds().forEach(recipient -> notificationService.notify(new NotifyCommand(
                recipient, NotificationType.PROJECT_COMPLETED, "Project", event.projectId(),
                message, "completed:%d:%d".formatted(event.projectId(), recipient))));
    }

    /** 이름을 못 찾으면 알림을 포기하지 않는다 — 문구만 덜 친절해진다. */
    private String nameOf(long personId) {
        return personDirectoryService.findRefs(List.of(personId)).stream()
                .map(PersonRef::name)
                .findFirst()
                .orElse("#" + personId);
    }
}
