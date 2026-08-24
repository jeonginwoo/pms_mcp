/*
 * 감사 행 표 — 통합 로그(G1-3)와 프로젝트 이력(G2-2)이 **같은 행**을 보여 주므로
 * 표도 하나다. 두 화면이 각자 표를 그리면 같은 데이터가 두 모양으로 보인다.
 *
 * `before`/`after`는 바뀐 필드만 담고 값의 타입이 필드마다 다르다. 여기서 문자열로
 * 만들어 "필드: 이전 → 이후" 한 줄씩 보여 준다 — 서버가 이미 diff를 해 뒀으므로
 * 화면이 두 스냅샷을 비교할 일은 없다.
 *
 * 정렬을 화면이 뒤집지 않는다: 이력은 시간 순서가 의미의 일부라 저장소 메서드가
 * 최신순을 정하고, 호출자에게 그 선택지가 없다.
 *
 * **사람 id는 이름으로 보여 준다**(2026-08-24): 감사 행은 id만 담으므로
 * `managerId: 18 → 19`처럼 나오는데, 그러면 "누가 누구로 바뀌었는지"를 읽는 사람이
 * 매번 인력 목록에서 찾아야 한다. 이름은 **이미 받아 둔 명부**에서 붙인다(추가 조회
 * 없음) — 명부에 없으면 `#18`로 남긴다. id를 지우지는 않고 툴팁에 남긴다.
 */
import { AUDIT_ACTION_COLOR, AUDIT_ACTION_LABEL, auditTime } from '../labels'
import { Empty } from './ui'
import type { AuditRecord } from '../types/api'

/**
 * 값이 사람 id인 필드 — 서버 스냅샷의 필드명 그대로다
 * (`ProjectAuditRecorder.snapshot`: Project는 managerId, ProjectAssignment는 personId).
 * 여기 없는 필드는 사람이 아니므로 그대로 보여 준다.
 */
const PERSON_FIELDS = new Set(['managerId', 'personId'])

interface Props {
  rows: AuditRecord[]
  /** 통합 로그는 어떤 대상인지 보여야 하고, 프로젝트 이력은 대상이 자명하다 */
  showTarget: boolean
  /** 사람 id → 이름. 감사 행은 id만 담아서 이름은 호출자가 붙인다 */
  nameOf: (personId: number) => string
  empty: string
}

export default function AuditTable({ rows, showTarget, nameOf, empty }: Props) {
  const grid = showTarget
    ? '108px 76px minmax(0,1fr) minmax(0,1.6fr) 84px 44px'
    : '108px 76px minmax(0,2.2fr) 84px 44px'

  if (rows.length === 0) {
    return <Empty>{empty}</Empty>
  }

  return (
    <>
      <div className="thead" style={{ gridTemplateColumns: grid }}>
        <span>시각</span>
        <span>행위</span>
        {showTarget && <span>대상</span>}
        <span>변경 내용</span>
        <span>수행자</span>
        <span>입구</span>
      </div>

      {rows.map((row) => (
        <div key={row.id} className="trow" style={{ gridTemplateColumns: grid, alignItems: 'start' }}>
          <span className="muted2" style={{ fontSize: 12 }}>{auditTime(row.createdAt)}</span>
          <ActionBadge action={row.action} />
          {showTarget && (
            <span className="muted" style={{ fontSize: 12.5 }}>
              {row.entityType}
              {row.entityId !== null && <span className="muted2"> #{row.entityId}</span>}
            </span>
          )}
          <Changes before={row.before} after={row.after} nameOf={nameOf} />
          <span className="muted" style={{ fontSize: 12.5 }} title={`personId ${row.actorId}`}>
            {nameOf(row.actorId)}
          </span>
          {/* MCP만 표시한다 — 대부분이 WEB이라 전부 칠하면 구분이 죽는다 */}
          <span className={row.source === 'MCP' ? 'badge' : 'muted2'} style={{ fontSize: 11 }}>
            {row.source === 'MCP' ? 'MCP' : '웹'}
          </span>
        </div>
      ))}
    </>
  )
}

function ActionBadge({ action }: { action: AuditRecord['action'] }) {
  const [color, background] = AUDIT_ACTION_COLOR[action]

  return (
    <span className="badge" style={{ color, background, fontSize: 11 }}>
      {AUDIT_ACTION_LABEL[action]}
    </span>
  )
}

/** 바뀐 필드만 "이전 → 이후"로. 생성·삭제는 한쪽이 비어 있다. */
function Changes({ before, after, nameOf }: {
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
  nameOf: (personId: number) => string
}) {
  const fields = [...new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})])]

  if (fields.length === 0) {
    return <span className="muted2" style={{ fontSize: 12 }}>—</span>
  }

  return (
    <div style={{ display: 'grid', gap: 2, minWidth: 0 }}>
      {fields.map((field) => (
        <div key={field} style={{ fontSize: 12, minWidth: 0, overflowWrap: 'anywhere' }}>
          <span className="muted2">{field}</span>{' '}
          {before?.[field] !== undefined && (
            <>
              <span className="muted" title={idTitle(field, before[field])}>
                {show(before[field], field, nameOf)}
              </span>
              <span className="muted2"> → </span>
            </>
          )}
          <span title={idTitle(field, after?.[field])}>
            {show(after?.[field], field, nameOf)}
          </span>
        </div>
      ))}
    </div>
  )
}

/**
 * 값의 타입이 필드마다 다르다 — 화면은 표시만 하므로 문자열로 만든다(`any` 금지).
 * 사람 id 필드는 이름으로 바꿔 준다.
 */
function show(value: unknown, field: string, nameOf: (personId: number) => string): string {
  if (value === null || value === undefined) {
    return '—'
  }

  if (PERSON_FIELDS.has(field) && typeof value === 'number') {
    return nameOf(value)
  }

  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }

  return JSON.stringify(value)
}

/** 이름으로 바꾼 자리에도 id는 남긴다 — 이력은 추적용이라 원값을 잃으면 안 된다. */
function idTitle(field: string, value: unknown): string | undefined {
  return PERSON_FIELDS.has(field) && typeof value === 'number'
    ? `personId ${value}`
    : undefined
}
