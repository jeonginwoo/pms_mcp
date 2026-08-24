/*
 * 유지보수 계약 목록 (부록 A `/maintenance` · AC D4-1) — 시트를 대체하는 화면이다.
 *
 * **전사 공개다**(D4-3): 조직 가시성이 걸리지 않고 404 은닉도 없다. 계약·이슈는 팀
 * 경계 없는 회사 공용 자산이라는 게 게이트 P에서 확인된 규칙이고, 그래서 화자를
 * 바꿔도 같은 목록이 나온다 — 프로젝트·인력 화면과 다른 점이다.
 *
 * `keyword`는 **서버로 내려보낸다**: 계약명·계약사에 더해 **사이트명**까지 맞아야
 * 하는데(45사이트 계약에 고객사명으로 도달하는 유일한 경로), 사이트는 목록 응답에
 * 실려 오지 않으므로 화면에서 거를 수 없다. 맞은 사이트는 `matchedSites`로 온다.
 *
 * 계약 등록(D2-1)은 **"계약 관리" 플래그 보유자에게만** 보인다(관리자·부문장·팀장 —
 * 상위 PRD §4-3). 조회는 전사인데 쓰기는 아닌 화면이라 두 규칙이 한 화면에 있다.
 * 삭제 버튼은 없다: 계약 종료는 상태 `종료`로 표현한다(D2-2 — 연 단위 갱신 이력 보존).
 */
import { useCallback, useEffect, useState } from 'react'
import { useStore } from '../store'
import { Empty, ErrorText } from '../components/ui'
import ContractEditModal from '../components/ContractEditModal'
import { period } from '../labels'
import type { ContractStatus, ContractSummary } from '../types/api'

/** 질의에는 이름을, 표시에는 서버가 준 라벨을 쓴다 (types/api.ts의 비대칭 주석 참조). */
const STATUS_FILTER: { value: ContractStatus; label: string }[] = [
  { value: 'PLANNED', label: '예정' },
  { value: 'NEW', label: '신규' },
  { value: 'ACTIVE', label: '유지' },
  { value: 'ENDED', label: '종료' },
]

const GRID = 'minmax(0,1.6fr) minmax(0,1fr) 68px minmax(120px,1fr) 58px'

type Load =
  | { phase: 'loading' }
  | { phase: 'ready'; rows: ContractSummary[]; total: number }
  | { phase: 'error'; code: string; message: string }

export default function Maintenance() {
  const { me, loadContracts, openContract } = useStore()
  const [status, setStatus] = useState<ContractStatus | 'ALL'>('ALL')
  const [keyword, setKeyword] = useState('')
  // 입력마다 질의하지 않는다 — 확정된 검색어만 서버로 간다
  const [submitted, setSubmitted] = useState('')
  const [load, setLoad] = useState<Load>({ phase: 'loading' })
  const [creating, setCreating] = useState(false)
  const writable = me?.manageContracts === true

  const fetchRows = useCallback(async () => {
    setLoad({ phase: 'loading' })
    const result = await loadContracts({
      status: status === 'ALL' ? null : status,
      keyword: submitted === '' ? null : submitted,
    })

    setLoad(result.ok
      ? { phase: 'ready', rows: result.value.content, total: result.value.totalElements }
      : { phase: 'error', code: result.error.code, message: result.error.message })
  }, [loadContracts, status, submitted])

  useEffect(() => { void fetchRows() }, [fetchRows])

  return (
    <section className="card">
      <div className="card-head">
        <h2>
          유지보수 계약{' '}
          {load.phase === 'ready' && (
            <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>
              {load.total}건
            </span>
          )}
        </h2>
        <form style={{ display: 'flex', gap: 8 }}
          onSubmit={(e) => { e.preventDefault(); setSubmitted(keyword.trim()) }}>
          <input placeholder="계약명 · 계약사 · 사이트명 검색" value={keyword}
            onChange={(e) => setKeyword(e.target.value)} style={{ width: 240 }} />
          <button className="btn btn-ghost" type="submit">검색</button>
          {writable && (
            <button className="btn btn-primary" type="button" onClick={() => setCreating(true)}>
              + 계약 등록
            </button>
          )}
        </form>
      </div>

      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 14 }}>
        <button className={`chip-btn ${status === 'ALL' ? 'on' : ''}`}
          onClick={() => setStatus('ALL')}>전체</button>
        {STATUS_FILTER.map((item) => (
          <button key={item.value} className={`chip-btn ${status === item.value ? 'on' : ''}`}
            onClick={() => setStatus(item.value)}>{item.label}</button>
        ))}
        {submitted !== '' && (
          <button className="chip-btn on" title="검색어 지우기"
            onClick={() => { setKeyword(''); setSubmitted('') }}>
            &quot;{submitted}&quot; ✕
          </button>
        )}
      </div>

      {load.phase === 'loading' && <Empty>불러오는 중…</Empty>}
      {load.phase === 'error' && <ErrorText code={load.code} message={load.message} />}

      {load.phase === 'ready' && (
        <>
          <div className="thead" style={{ gridTemplateColumns: GRID }}>
            <span>계약</span>
            <span>계약사</span>
            <span>상태</span>
            <span>기간</span>
            <span>사이트</span>
          </div>

          {load.rows.map((row) => (
            <button key={row.id} className="trow" style={{ gridTemplateColumns: GRID, padding: '12px 2px' }}
              onClick={() => void openContract(row.id)}>
              <div style={{ minWidth: 0 }}>
                <div style={{ fontWeight: 700, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {row.name}
                </div>
                {/* 사이트명으로 맞은 계약임을 보여 준다 — 이름만 봐서는 왜 걸렸는지 알 수 없다 */}
                {row.matchedSites.length > 0 && (
                  <div className="muted2" style={{ fontSize: 11.5, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    사이트 일치: {row.matchedSites.join(' · ')}
                  </div>
                )}
              </div>
              <span className="muted">{row.contractor}</span>
              <span className="badge" style={{ fontSize: 11.5 }}>{row.status}</span>
              <span className="muted" style={{ fontSize: 12 }}>
                {period(row.startDate, row.endDate)}
              </span>
              <span className="muted">{row.siteCount}곳</span>
            </button>
          ))}

          {load.rows.length === 0 && (
            <Empty>
              {submitted === ''
                ? '유지보수 계약이 없습니다.'
                : `"${submitted}"에 맞는 계약이 없습니다 — 계약명·계약사·사이트명으로 찾습니다.`}
            </Empty>
          )}
        </>
      )}

      <div className="muted2" style={{ fontSize: 11.5, marginTop: 12 }}>
        유지보수는 <b>전사 공개</b>입니다(D4-3) — 조직 가시성이 걸리지 않고 화자에 따라 달라지지
        않습니다. 등록·수정은 <b>계약 관리</b> 권한이 있어야 하고, 계약 종료는 삭제가 아니라
        상태 <b>종료</b>로 표현합니다(연 단위 갱신 이력 보존).
      </div>

      {creating && (
        <ContractEditModal contract={null} onClose={() => { setCreating(false); void fetchRows() }} />
      )}
    </section>
  )
}
