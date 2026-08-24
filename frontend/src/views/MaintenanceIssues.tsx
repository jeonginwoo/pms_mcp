/*
 * 유지보수 이슈 목록 (부록 A `/maintenance/issues` · AC D3-4) — 구 이슈 게시판을 대체한다.
 *
 * **담당자·고객사 컬럼을 상시 노출**하고 **미배정 필터**를 둔다(부록 A). 미배정은
 * 화면에서 거르지 않고 서버 `?unassigned=true`로 내려보낸다 — 시드에서 신규 예정·종료
 * 사이트의 담당이 비어 있어 실제로 쓰이는 필터다.
 *
 * 전사 공개다(D4-3) — 화자에 따라 목록이 달라지지 않는다.
 *
 * 쓰기가 붙었다(2026-08-24 — D3-1·D3-2·D3-3): 등록은 헤더의 버튼, 처리·코멘트는 행을
 * 눌러 여는 모달이다. **권한으로 감추지 않는다** — US-D3은 로그인 사용자 전체다
 * (계약 쓰기가 "계약 관리" 플래그로 감추는 것과 다른 자리다).
 */
import { useCallback, useEffect, useState } from 'react'
import { useStore } from '../store'
import { Empty, ErrorText } from '../components/ui'
import { shortDate } from '../labels'
import IssueRegisterModal from '../components/IssueRegisterModal'
import IssueDetailModal from '../components/IssueDetailModal'
import type { IssueStatus, IssueType, IssueView } from '../types/api'

/** 질의는 이름으로, 표시는 서버가 준 라벨로 (types/api.ts의 비대칭 주석 참조). */
const STATUS_FILTER: { value: IssueStatus; label: string }[] = [
  { value: 'RECEIVED', label: '접수' },
  { value: 'IN_PROGRESS', label: '처리중' },
  { value: 'AWAITING_CLIENT', label: '고객확인대기' },
  { value: 'DONE', label: '완료' },
]

const TYPE_FILTER: { value: IssueType; label: string }[] = [
  { value: 'INCIDENT', label: '장애' },
  { value: 'INQUIRY', label: '문의' },
  { value: 'REQUEST', label: '요청' },
]

const GRID = 'minmax(0,2fr) 62px 76px minmax(0,1fr) minmax(0,1fr) 78px'

type Load =
  | { phase: 'loading' }
  | { phase: 'ready'; rows: IssueView[]; total: number }
  | { phase: 'error'; code: string; message: string }

export default function MaintenanceIssues() {
  const { me, loadIssues, openContract } = useStore()
  const [status, setStatus] = useState<IssueStatus | 'ALL'>('ALL')
  const [type, setType] = useState<IssueType | 'ALL'>('ALL')
  const [scope, setScope] = useState<'ALL' | 'MINE' | 'UNASSIGNED'>('ALL')
  const [load, setLoad] = useState<Load>({ phase: 'loading' })
  const [registering, setRegistering] = useState(false)
  // 행을 누르면 그 이슈의 처리·코멘트 모달이 열린다
  const [opened, setOpened] = useState<IssueView | null>(null)

  const fetchRows = useCallback(async () => {
    setLoad({ phase: 'loading' })
    const result = await loadIssues({
      status: status === 'ALL' ? null : status,
      type: type === 'ALL' ? null : type,
      // "내 담당"과 "미배정"은 서로 배타적이다 — 서버 파라미터도 둘 중 하나만 간다
      assigneeId: scope === 'MINE' ? (me?.id ?? null) : null,
      unassigned: scope === 'UNASSIGNED',
    })

    setLoad(result.ok
      ? { phase: 'ready', rows: result.value.content, total: result.value.totalElements }
      : { phase: 'error', code: result.error.code, message: result.error.message })
  }, [loadIssues, status, type, scope, me?.id])

  useEffect(() => { void fetchRows() }, [fetchRows])

  return (
    <section className="card">
      <div className="card-head">
        <h2>
          유지보수 이슈{' '}
          {load.phase === 'ready' && (
            <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>
              {load.total}건
            </span>
          )}
        </h2>
        <div style={{ display: 'flex', gap: 6 }}>
          <button className={`chip-btn ${scope === 'ALL' ? 'on' : ''}`}
            onClick={() => setScope('ALL')}>전체</button>
          <button className={`chip-btn ${scope === 'MINE' ? 'on' : ''}`}
            onClick={() => setScope('MINE')}>내 담당</button>
          <button className={`chip-btn ${scope === 'UNASSIGNED' ? 'on' : ''}`}
            title="담당 엔지니어가 지정되지 않은 이슈"
            onClick={() => setScope('UNASSIGNED')}>미배정</button>
          <button className="btn btn-primary btn-sm" style={{ marginLeft: 6 }}
            onClick={() => setRegistering(true)}>+ 이슈 등록</button>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 8 }}>
        <button className={`chip-btn ${status === 'ALL' ? 'on' : ''}`}
          onClick={() => setStatus('ALL')}>상태 전체</button>
        {STATUS_FILTER.map((item) => (
          <button key={item.value} className={`chip-btn ${status === item.value ? 'on' : ''}`}
            onClick={() => setStatus(item.value)}>{item.label}</button>
        ))}
      </div>

      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 14 }}>
        <button className={`chip-btn ${type === 'ALL' ? 'on' : ''}`}
          onClick={() => setType('ALL')}>유형 전체</button>
        {TYPE_FILTER.map((item) => (
          <button key={item.value} className={`chip-btn ${type === item.value ? 'on' : ''}`}
            onClick={() => setType(item.value)}>{item.label}</button>
        ))}
      </div>

      {load.phase === 'loading' && <Empty>불러오는 중…</Empty>}
      {load.phase === 'error' && <ErrorText code={load.code} message={load.message} />}

      {load.phase === 'ready' && (
        <>
          <div className="thead" style={{ gridTemplateColumns: GRID }}>
            <span>제목</span>
            <span>유형</span>
            <span>상태</span>
            <span>담당자</span>
            <span>고객사</span>
            <span>접수일</span>
          </div>

          {load.rows.map((issue) => (
            <div key={issue.id} className="trow" style={{ gridTemplateColumns: GRID }}>
              <div style={{ minWidth: 0 }}>
                <button style={{ fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden',
                  textOverflow: 'ellipsis', background: 'none', border: 'none', padding: 0,
                  cursor: 'pointer', color: 'inherit', font: 'inherit', textAlign: 'left',
                  maxWidth: '100%' }}
                  title="처리·코멘트" onClick={() => setOpened(issue)}>
                  {issue.title}
                </button>
                {/* 계약에 붙지 않은 이슈가 시드에 절반이다 — 링크는 있을 때만 그린다 */}
                {issue.contractId !== null && (
                  <button className="muted2"
                    style={{ background: 'none', border: 'none', padding: 0, fontSize: 11.5, textDecoration: 'underline', cursor: 'pointer' }}
                    onClick={() => void openContract(issue.contractId as number)}>
                    {issue.contractName ?? `계약 #${issue.contractId}`}
                  </button>
                )}
              </div>
              <span className="badge" style={{ fontSize: 11 }}>{issue.type}</span>
              <span className="badge" style={{ fontSize: 11 }}>{issue.status}</span>
              <span className={issue.assignee ? 'muted' : 'muted2'}>
                {issue.assignee?.name ?? '미배정'}
              </span>
              <span className="muted">{issue.siteName ?? '—'}</span>
              <span className="muted2" style={{ fontSize: 12 }}>{shortDate(issue.receivedAt)}</span>
            </div>
          ))}

          {load.rows.length === 0 && (
            <Empty>
              {scope === 'MINE'
                ? '내가 담당인 이슈가 없습니다.'
                : scope === 'UNASSIGNED'
                  ? '미배정 이슈가 없습니다.'
                  : '조건에 맞는 이슈가 없습니다.'}
            </Empty>
          )}
        </>
      )}

      <div className="muted2" style={{ fontSize: 11.5, marginTop: 12 }}>
        전사 공개입니다(D4-3) — 등록·처리·코멘트는 로그인 사용자 전체가 할 수 있습니다(US-D3).
        제목을 누르면 상태 전이·담당 재배정·코멘트를 볼 수 있습니다.
      </div>
      {registering && (
        <IssueRegisterModal onClose={() => setRegistering(false)}
          onRegistered={() => void fetchRows()} />
      )}

      {/* 처리·코멘트 뒤에 목록을 다시 읽는다 — 상태·담당·코멘트 수가 표에 있다 */}
      {opened !== null && (
        <IssueDetailModal issue={opened} onClose={() => setOpened(null)}
          onChanged={() => void fetchRows()} />
      )}
    </section>
  )
}
