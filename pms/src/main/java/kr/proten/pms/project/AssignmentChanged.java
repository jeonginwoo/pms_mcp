package kr.proten.pms.project;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 배정이 바뀌었다 — project가 발행하고 resource·notification이 구독한다 (§8).
 *
 * <p><b>§8의 세 이벤트를 한 타입에 담고 {@link Kind}로 가른다.</b> 세 이벤트가 하는 말이
 * "이 사람의 이 기간 배정이 달라졌다"로 같고, 구독자 대부분이 같은 일을 하기 때문이다 —
 * resource는 셋 다 재계산이고 notification만 {@code ASSIGNED}에서 갈린다. 타입을 셋으로
 * 나누면 구독자마다 같은 본문을 세 번 쓰거나 공통 상위 타입을 또 만들게 된다.
 * 구분이 필요한 곳은 {@code kind}로 묻는다.
 *
 * <p><b>영향 월을 계산해 실어 보낸다</b>: 가동률은 월 단위이고 배정 하나가 여러 달에
 * 걸친다. 구독자가 날짜에서 월을 다시 뽑게 하면 "어느 달이 영향을 받았는가"라는 판단이
 * 구독자 수만큼 생긴다 — 그 판단은 배정을 아는 project의 것이다.
 *
 * @param personId 배정 대상 — 가동률·알림 둘 다 사람 단위다
 */
public record AssignmentChanged(
        Kind kind,
        long projectId,
        String projectName,
        long personId,
        List<YearMonth> affectedMonths) {

    /** 무엇이 일어났나 — §8의 세 이벤트에 대응한다. */
    public enum Kind {
        /** `MemberAssignedToProject` — 새 배정. 알림 대상이 되는 유일한 종류다 */
        ASSIGNED,
        /** `AssignmentUpdated` — 기간·M/M 변경 (B1-4) */
        UPDATED,
        /** `AssignmentClosed` — 종료. 종료월 이후가 빠진다 (B2-1) */
        CLOSED
    }

    /**
     * 기간이 걸치는 달 — 시작·종료가 없으면 그 자리를 오늘로 본다.
     *
     * <p>상한을 두는 이유: 기간이 비정상적으로 길면(시드에 2015~2031 계약이 있다)
     * 재계산이 수백 달을 돈다. 가동률이 실제로 쓰이는 창은 좁으므로 잘라도 된다 —
     * 잘린 달은 다음 조회가 어차피 그 시점에 계산한다(캐시가 없다).
     */
    public static List<YearMonth> monthsOf(LocalDate startDate, LocalDate endDate, YearMonth today) {
        YearMonth from = startDate == null ? today : YearMonth.from(startDate);
        YearMonth to = endDate == null ? today : YearMonth.from(endDate);

        if (to.isBefore(from)) {
            to = from;
        }

        List<YearMonth> months = new ArrayList<>();

        for (YearMonth month = from; !month.isAfter(to) && months.size() < MAX_MONTHS;
                month = month.plusMonths(1)) {
            months.add(month);
        }

        // 오늘이 기간 밖이어도 확인한다 — "지금 과부하인가"가 알림의 관심사다
        if (!months.contains(today)) {
            months.add(today);
        }

        return List.copyOf(months);
    }

    private static final int MAX_MONTHS = 24;
}
