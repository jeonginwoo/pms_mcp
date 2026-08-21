/*
 * 기간 입력 규칙 — 종료일은 시작일보다 뒤여야 한다 (2026-08-22 결정).
 *
 * 서버(Project·ProjectAssignment 엔티티)가 같은 규칙으로 400을 돌려주지만, 날짜 입력은
 * 눌러 보기 전에 알려 주는 편이 낫다 — 화면은 고를 수 없는 날짜를 막고 문구를 먼저 띄운다.
 */

/** 한쪽이 비어 있으면 비교할 것이 없다 — 통과다(계약 전 단계의 열린 기간). */
export function invalidPeriodMessage(startDate: string, endDate: string): string | null {
  if (startDate === '' || endDate === '') {
    return null
  }

  return endDate > startDate ? null : '종료일은 시작일보다 뒤여야 합니다'
}

/** date input의 min — 시작일 다음 날부터 고를 수 있게 한다. */
export function minEndDate(startDate: string): string | undefined {
  if (startDate === '') {
    return undefined
  }

  const next = new Date(startDate)
  next.setDate(next.getDate() + 1)

  return next.toISOString().slice(0, 10)
}
