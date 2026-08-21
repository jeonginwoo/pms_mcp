/*
 * 표시용 라벨·색 — 서버는 열거 값을 이름(CONTRACT_PENDING 등)으로 직렬화하므로
 * 한국어 표기는 화면이 갖는다. 정본은 서버 열거형의 label()이며, 값이 늘면
 * 여기에도 한 줄 추가한다(누락 시 이름이 그대로 보이도록 폴백을 둔다).
 */
import type { Engagement, ProjectPhase, ProjectRole, ProjectStatus } from './types/api'

export const STATUS_LABEL: Record<ProjectStatus, string> = {
  CONTRACT_PENDING: '계약대기',
  ORDER_CONFIRMED: '수주확정',
  IN_PROGRESS: '진행중',
  COMPLETED: '완료',
  UNDER_MAINTENANCE: '유지보수중',
}

/** 상태 순서 = §5 전이 순서 — 필터 칩·상태 선택의 나열 순서 */
export const STATUS_ORDER: ProjectStatus[] = [
  'CONTRACT_PENDING', 'ORDER_CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'UNDER_MAINTENANCE',
]

/** [글자색, 배경색] */
export const STATUS_COLOR: Record<ProjectStatus, [string, string]> = {
  CONTRACT_PENDING: ['#8b93a3', 'rgba(139,147,163,.15)'],
  ORDER_CONFIRMED: ['#6b5bd2', 'rgba(107,91,210,.12)'],
  IN_PROGRESS: ['#2f6fed', 'rgba(47,111,237,.12)'],
  COMPLETED: ['#1f9d57', 'rgba(31,157,87,.13)'],
  UNDER_MAINTENANCE: ['#b9820f', 'rgba(185,130,15,.13)'],
}

export const PHASE_LABEL: Record<ProjectPhase, string> = {
  SALES: '영업',
  SOLUTION: '솔루션',
}

export const ENGAGEMENT_LABEL: Record<Engagement, string> = {
  REMOTE: '원격',
  ONSITE: '상주',
  PARTIAL_ONSITE: '부분상주',
}

export const ROLE_LABEL: Record<ProjectRole, string> = {
  PM: 'PM',
  PL: 'PL',
  PARTICIPANT: '참여자',
}

/**
 * 정보 수정 경로(A5)로 갈 수 있는 다음 상태 — 없으면 null.
 *
 * 서버 `ProjectStatus.next()`와 같은 표를 화면이 한 번 더 갖는다: 상위 PRD §4-1이
 * "프론트는 UI 노출 제어만, 최종 판정은 서버"로 정한 역할 분담이고, 서버가 어차피
 * 409 INVALID_TRANSITION으로 막는다. 고를 수 없는 값을 선택지에 두지 않기 위한 표시용이다.
 */
export function nextStatus(status: ProjectStatus): ProjectStatus | null {
  if (status === 'CONTRACT_PENDING') {
    return 'ORDER_CONFIRMED'
  }

  if (status === 'ORDER_CONFIRMED') {
    return 'IN_PROGRESS'
  }

  return null
}

/** yyyy-MM-dd → yy.MM.dd (없으면 —) */
export function shortDate(date: string | null): string {
  if (!date) {
    return '—'
  }

  return date.slice(2).replace(/-/g, '.')
}

export function period(startDate: string | null, endDate: string | null): string {
  if (!startDate && !endDate) {
    return '—'
  }

  return `${shortDate(startDate)} ~ ${shortDate(endDate)}`
}

/** 마감까지 남은 일수 표기 — 기간이 없으면 표시하지 않는다 */
export function dday(endDate: string | null): { text: string; color: string } | null {
  if (!endDate) {
    return null
  }

  const days = Math.round((new Date(endDate).getTime() - Date.now()) / 86_400_000)

  if (days < 0) {
    return { text: '종료', color: 'var(--muted2)' }
  }

  if (days <= 30) {
    return { text: `D-${days}`, color: 'var(--danger)' }
  }

  if (days <= 90) {
    return { text: `D-${days}`, color: 'var(--warn)' }
  }

  return { text: `D-${days}`, color: 'var(--muted2)' }
}
