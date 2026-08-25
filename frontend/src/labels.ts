/*
 * 표시용 라벨·색 — 서버는 열거 값을 이름(CONTRACT_PENDING 등)으로 직렬화하므로
 * 한국어 표기는 화면이 갖는다. 정본은 서버 열거형의 label()이며, 값이 늘면
 * 여기에도 한 줄 추가한다(누락 시 이름이 그대로 보이도록 폴백을 둔다).
 */
import type {
  AuditAction,
  ContractStatus,
  Engagement,
  IssueStatus,
  IssueType,
  NotificationType,
  ProjectPhase,
  ProjectRole,
  ProjectStatus,
  VisibilityScope,
} from './types/api'

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
 * 감사 행위 — 서버 열거를 이름으로 받으므로 여기서 한국어를 붙인다.
 * STATE_CHANGE는 §5 상태 전이 전용이고 그 밖의 변경은 전부 UPDATE다(v2.1 정리).
 */
export const AUDIT_ACTION_LABEL: Record<AuditAction, string> = {
  CREATE: '생성',
  UPDATE: '수정',
  DELETE: '삭제',
  STATE_CHANGE: '상태 전이',
}

export const AUDIT_ACTION_COLOR: Record<AuditAction, [string, string]> = {
  CREATE: ['#1f9d57', 'rgba(31,157,87,.13)'],
  UPDATE: ['#2f6fed', 'rgba(47,111,237,.12)'],
  DELETE: ['#d84a4a', 'rgba(216,74,74,.13)'],
  STATE_CHANGE: ['#6b5bd2', 'rgba(107,91,210,.12)'],
}

/** 감사 시각 — Instant(ISO)라 초까지만 보여 준다. */
export function auditTime(createdAt: string): string {
  const at = new Date(createdAt)

  return `${String(at.getFullYear()).slice(2)}.${pad(at.getMonth() + 1)}.${pad(at.getDate())} `
    + `${pad(at.getHours())}:${pad(at.getMinutes())}`
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
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

/**
 * 알림 유형 (EPIC F · H1-4 설정 단위).
 * 정본은 서버 `NotificationType`의 label()이고, 유형이 늘면 여기에도 한 줄 추가한다.
 */
export const NOTIFICATION_TYPE_LABEL: Record<NotificationType, string> = {
  ASSIGNED: '프로젝트 배정',
  OVERBOOKED: '과부하 감지',
  PROJECT_COMPLETED: '프로젝트 완료',
  DEADLINE_NEAR: '마감 임박',
  COMPLETION_OVERDUE: '완료 지연',
  ISSUE_ASSIGNED: '이슈 담당 지정',
}

/** 가시성 범위 (상위 PRD §4-4) — 권한 그룹 관리 화면의 select 표기 */
export const VISIBILITY_SCOPE_LABEL: Record<VisibilityScope, string> = {
  COMPANY: '전사',
  DIVISION: '부문',
  TEAM: '팀(하위 포함)',
  SELF: '본인만',
}

export const VISIBILITY_SCOPE_ORDER: VisibilityScope[] =
  ['COMPANY', 'DIVISION', 'TEAM', 'SELF']

/** 계약 상태 (§4) — 질의는 이름, 표시는 서버가 준 라벨이라 여기 표는 **선택지 전용**이다 */
export const CONTRACT_STATUS_LABEL: Record<ContractStatus, string> = {
  PLANNED: '예정',
  NEW: '신규',
  ACTIVE: '유지',
  ENDED: '종료',
}

export const CONTRACT_STATUS_ORDER: ContractStatus[] = ['PLANNED', 'NEW', 'ACTIVE', 'ENDED']

/** 연락처 구분 (§4) */
export const CONTACT_PARTY_LABEL: Record<'CONTRACTOR' | 'CLIENT', string> = {
  CONTRACTOR: '계약사',
  CLIENT: '고객사',
}

/**
 * 이슈 상태 (AC D3-2) · 이슈 유형 (§4) — 목록 필터와 처리 화면이 공유한다.
 *
 * 질의는 열거 이름으로 보내고 표시는 서버가 준 한국어 라벨을 쓰는 것이 원칙이지만
 * (`types/api.ts`의 비대칭 주석), **필터 버튼과 상태 전이 버튼은 서버 응답이 없는
 * 상태에서 그려야** 해서 여기에 표가 필요하다. 서버 열거가 늘면 여기도 한 줄 늘고,
 * 그 누락은 타입 검사가 잡는다(`Record<IssueStatus, ...>`).
 */
export const ISSUE_STATUS_LABEL: Record<IssueStatus, string> = {
  RECEIVED: '접수',
  IN_PROGRESS: '처리중',
  AWAITING_CLIENT: '고객확인대기',
  DONE: '완료',
}

export const ISSUE_TYPE_LABEL: Record<IssueType, string> = {
  INCIDENT: '장애',
  INQUIRY: '문의',
  REQUEST: '요청',
}

/**
 * 그 상태에서 갈 수 있는 다음 상태 (AC D3-2) — **서버 `IssueStatus.canTransitionTo`의
 * 사본**이다. 화면이 이 표를 갖는 이유는 버튼을 그리려면 눌러 보기 전에 알아야
 * 하기 때문이고, 정본은 서버다: 여기서 허용한 전이도 서버가 409로 거절할 수 있고
 * 그때는 화면이 그 오류를 그대로 보여 준다(막는 쪽을 두 벌 두지 않는다).
 *
 * 고객확인대기는 선택이라 처리중에서 완료로 바로 갈 수 있고, 역방향은 재개
 * (완료 → 처리중) 하나만 열려 있다.
 */
export const ISSUE_NEXT_STATUSES: Record<IssueStatus, IssueStatus[]> = {
  RECEIVED: ['IN_PROGRESS'],
  IN_PROGRESS: ['AWAITING_CLIENT', 'DONE'],
  AWAITING_CLIENT: ['DONE'],
  DONE: ['IN_PROGRESS'],
}
