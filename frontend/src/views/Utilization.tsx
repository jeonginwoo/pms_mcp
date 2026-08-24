/*
 * 가동률 대시보드 (부록 A `/utilization` · AC C1-1~C1-6).
 *
 * 목록은 서버가 화자의 가시성 범위로 걸러 준 것이다 — 범위 밖은 애초에 오지 않는다.
 * 집계에는 `billable=false` 인원이 빠지고(C1-5) 지원 조직 인원이 목록에 없는 것은
 * 그 규칙의 결과다.
 *
 * **과부하 판정을 화면이 다시 하지 않는다**: `?overbooked=true`를 서버에 요청하고
 * 강조 표시만 `기본 > 100`으로 맞춘다. 판정 기준은 언제나 기본 가동률이고 보정은
 * 단가 가중 보조 지표다(2026-08-10 재정의) — 이 구분이 흐려지면 화면이 다른
 * 오버부킹 명단을 만든다.
 *
 * 맨 위의 **내 가동률**은 집계와 별개로 `?personId=`로 한 번 더 묻는다(2026-08-24 신설):
 * 집계는 `billable=false`를 모집단에서 빼므로(C1-5) 지원 조직 인원은 빈 목록을 보는데
 * 본인은 값을 갖는다 — 개인 지정은 그 규칙과 무관하다.
 *
 * 팀 필터는 **받아 둔 목록에서 화면이 거른다**: 서버의 `?orgUnitId=`는 조직 id를
 * 요구하는데 `/api/org-units`는 관리 권한자만 부를 수 있어, 일반 화자에게는 id를
 * 얻을 경로가 없다. 응답이 `team`·`division`을 싣는 이유가 바로 이것이다(C1-6).
 */
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useStore } from '../store'
import { Empty, ErrorText, Metric } from '../components/ui'
import MyUtilizationCard from '../components/MyUtilizationCard'
import type { UtilizationView } from '../types/api'

/** 과부하 임계값 — 서버 판정(`기본 > 100`)과 같은 값이어야 한다 (AC C1-3). */
const OVERBOOKED_THRESHOLD = 100

const GRID = 'minmax(0,1.1fr) minmax(0,1fr) minmax(0,1fr) 78px minmax(120px,1.3fr) 92px'

type Load =
  | { phase: 'loading' }
  | { phase: 'ready'; rows: UtilizationView[] }
  | { phase: 'error'; code: string; message: string }

export default function Utilization() {
  const { me, loadUtilization } = useStore()
  const [month, setMonth] = useState(currentMonth)
  const [overbookedOnly, setOverbookedOnly] = useState(false)
  const [team, setTeam] = useState<string | 'ALL'>('ALL')
  const [load, setLoad] = useState<Load>({ phase: 'loading' })

  const fetchRows = useCallback(async () => {
    setLoad({ phase: 'loading' })
    const result = await loadUtilization({ month, overbooked: overbookedOnly })

    setLoad(result.ok
      ? { phase: 'ready', rows: result.value }
      : { phase: 'error', code: result.error.code, message: result.error.message })
  }, [loadUtilization, month, overbookedOnly])

  useEffect(() => { void fetchRows() }, [fetchRows])

  const rows = load.phase === 'ready' ? load.rows : []
  // 팀 칩은 응답에서 만든다 — 필터를 바꿔도 목록에 없는 팀이 남지 않는다
  const teams = useMemo(
    () => [...new Set(rows.map((row) => row.team))].sort((a, b) => a.localeCompare(b)),
    [rows])
  const visible = useMemo(
    () => (team === 'ALL' ? rows : rows.filter((row) => row.team === team)),
    [rows, team])
  const overbooked = visible.filter(isOverbooked).length
  // 집계에 내가 있는지 — 없으면 내 카드가 그 이유(C1-5)를 덧붙인다
  const inAggregate = rows.some((row) => row.personId === me?.id)

  return (
    <section className="card">
      <div className="card-head">
        <h2>
          가동률{' '}
          <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>
            {visible.length}명
          </span>
        </h2>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <input type="month" value={month} onChange={(e) => setMonth(e.target.value)}
            style={{ width: 150 }} />
          <button className={`chip-btn ${overbookedOnly ? 'on' : ''}`}
            title="서버가 기본 가동률 100% 초과인 사람만 돌려준다 (C1-3)"
            onClick={() => setOverbookedOnly((current) => !current)}>
            과부하만 보기
          </button>
        </div>
      </div>

      {/* 집계가 비어 있어도 보인다 — 그 경우가 이 카드가 있어야 하는 이유다 */}
      {load.phase === 'ready' && <MyUtilizationCard month={month} inAggregate={inAggregate} />}

      {load.phase === 'ready' && rows.length > 0 && (
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 14 }}>
          <Metric label="집계 인원" value={`${visible.length}명`} />
          <Metric label="과부하" value={`${overbooked}명`}
            color={overbooked > 0 ? 'var(--danger)' : undefined} />
          <Metric label="평균 기본" value={`${average(visible, (row) => row.basic)}%`} />
          <Metric label="평균 보정" value={`${average(visible, (row) => row.adjusted)}%`} />
        </div>
      )}

      {teams.length > 1 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 14 }}>
          <button className={`chip-btn ${team === 'ALL' ? 'on' : ''}`}
            onClick={() => setTeam('ALL')}>전체</button>
          {teams.map((name) => (
            <button key={name} className={`chip-btn ${team === name ? 'on' : ''}`}
              onClick={() => setTeam(name)}>{name}</button>
          ))}
        </div>
      )}

      {load.phase === 'loading' && <Empty>불러오는 중…</Empty>}

      {load.phase === 'error' && (
        <ErrorText code={load.code} message={load.message} />
      )}

      {load.phase === 'ready' && (
        <>
          <div className="thead" style={{ gridTemplateColumns: GRID }}>
            <span>이름</span>
            <span>팀</span>
            <span>부문</span>
            <span>배정 M/M</span>
            <span>기본 가동률</span>
            <span>보정</span>
          </div>

          {visible.map((row) => (
            <div key={row.personId} className="trow" style={{ gridTemplateColumns: GRID }}>
              <span style={{ fontWeight: 600 }}>{row.name}</span>
              <span className="muted">{row.team}</span>
              <span className="muted">{row.division}</span>
              <span className="muted">
                {round(row.assignedMm)}
                <span className="muted2"> / {round(row.availableMm)}</span>
              </span>
              <RateBar value={row.basic} />
              <span className="muted" style={{ fontVariantNumeric: 'tabular-nums' }}>
                {round(row.adjusted)}%
              </span>
            </div>
          ))}

          {visible.length === 0 && (
            <Empty>
              {overbookedOnly
                ? '과부하인 인원이 없습니다.'
                : '집계 대상이 없습니다 — 가시성 범위 안에 집계 모집단(billable) 인원이 없습니다.'}
            </Empty>
          )}
        </>
      )}

      <div className="muted2" style={{ fontSize: 11.5, marginTop: 12 }}>
        기본 = Σ배정 M/M ÷ 가용 M/M — 과부하 판정은 <b>언제나 기본</b>입니다(100% 초과).
        보정은 직급계수를 곱한 단가 가중 보조 지표로 판정에 쓰지 않습니다.
        집계에는 지원 조직 인원(billable 아님)이 빠집니다.
      </div>
    </section>
  )
}

/** 가동률 막대 — 100%를 기준선으로 두고 넘는 만큼을 강조한다. */
function RateBar({ value }: { value: number }) {
  const over = value > OVERBOOKED_THRESHOLD

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <div className="bar" style={{ height: 17, flex: 1 }}>
        {/* .bar-fill.over 는 styles.css가 이미 갖고 있다 — 색을 인라인으로 다시 정하지 않는다 */}
        <div className={`bar-fill ${over ? 'over' : ''}`}
          style={{ width: `${Math.min(value, OVERBOOKED_THRESHOLD)}%` }} />
      </div>
      <span style={{
        fontWeight: over ? 800 : 600,
        color: over ? 'var(--danger)' : undefined,
        fontSize: 12.5,
        fontVariantNumeric: 'tabular-nums',
        minWidth: 46,
        textAlign: 'right',
      }}>
        {round(value)}%
      </span>
    </div>
  )
}

function isOverbooked(row: UtilizationView): boolean {
  return row.basic > OVERBOOKED_THRESHOLD
}

/** 표시용 반올림 — 서버는 6자리로 노이즈만 떼어 주므로 자릿수는 화면이 정한다. */
function round(value: number): number {
  return Math.round(value * 10) / 10
}

function average(rows: UtilizationView[], pick: (row: UtilizationView) => number): number {
  if (rows.length === 0) {
    return 0
  }

  return round(rows.reduce((sum, row) => sum + pick(row), 0) / rows.length)
}

/** 기준 월 기본값 = 이번 달. `<input type="month">`가 주는 형식이 서버 형식과 같다. */
function currentMonth(): string {
  const now = new Date()

  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}
