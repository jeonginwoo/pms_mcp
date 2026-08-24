/*
 * 내 가동률 — 가동률 화면 맨 위의 개인 지정 조회 (AC C1-5의 "개인 지정은 billable과 무관").
 *
 * **왜 집계 목록과 따로 묻는가**: 집계는 `billable=false` 인원을 모집단에서 뺀다(C1-5).
 * 그래서 지원 조직(AX사업기획부·관리•마케팅부·대표) 인원은 대시보드를 열면 목록이
 * 비어 있는데, 정작 본인은 배정이 있어 가동률을 갖는다 — 실측: 윤종헌은 집계에서
 * 0명인 화면을 보지만 본인 값은 182%다. 서버는 `?personId=`로 그 값을 이미 내주므로
 * (개인 지정은 모집단 규칙과 무관하다) 한 번 더 물어 맨 위에 놓는다.
 *
 * 2026-08-24 신설 — 부록 A `/utilization`에 없던 요소이고 사용자 결정으로 추가했다.
 */
import { useCallback, useEffect, useState } from 'react'
import { useStore } from '../store'
import type { UtilizationView } from '../types/api'

interface Props {
  month: string
  /** 집계 목록에 내가 들어 있는가 — 없으면 그 이유(C1-5)를 한 줄 덧붙인다 */
  inAggregate: boolean
}

type Load =
  | { phase: 'loading' }
  | { phase: 'ready'; row: UtilizationView }
  /** 값이 없는 경우 — 가용 M/M이 0이면 가동률이라는 값 자체가 없다(시스템 계정 등) */
  | { phase: 'none' }

export default function MyUtilizationCard({ month, inAggregate }: Props) {
  const { me, loadUtilization } = useStore()
  const [load, setLoad] = useState<Load>({ phase: 'loading' })
  const personId = me?.id ?? null

  const fetchMine = useCallback(async () => {
    if (personId === null) {
      return
    }

    setLoad({ phase: 'loading' })
    const result = await loadUtilization({ month, personId })

    // 실패는 조용히 접는다 — 이 카드는 보조 정보이고, 목록 쪽 오류가 이미 화면에 뜬다
    setLoad(result.ok && result.value.length > 0
      ? { phase: 'ready', row: result.value[0] }
      : { phase: 'none' })
  }, [loadUtilization, month, personId])

  useEffect(() => { void fetchMine() }, [fetchMine])

  if (load.phase !== 'ready') {
    return null
  }

  const { row } = load
  const over = row.basic > 100

  return (
    <div className="card" style={{
      background: 'var(--soft)',
      padding: '14px 16px',
      marginBottom: 14,
      display: 'flex',
      alignItems: 'center',
      gap: 16,
      flexWrap: 'wrap',
    }}>
      <div style={{ minWidth: 0 }}>
        <div className="muted2" style={{ fontSize: 11, fontWeight: 600 }}>내 가동률</div>
        <div style={{ fontWeight: 700, fontSize: 13.5 }}>
          {row.name}
          <span className="muted" style={{ fontWeight: 500 }}> · {row.team}</span>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
        <span style={{
          fontSize: 22,
          fontWeight: 800,
          color: over ? 'var(--danger)' : undefined,
          fontVariantNumeric: 'tabular-nums',
        }}>
          {round(row.basic)}%
        </span>
        <span className="muted2" style={{ fontSize: 12 }}>보정 {round(row.adjusted)}%</span>
      </div>

      <span className="muted" style={{ fontSize: 12.5 }}>
        배정 {round(row.assignedMm)} / 가용 {round(row.availableMm)} M/M
      </span>

      {/* 목록이 비어 보이는 이유를 여기서 답한다 — 규칙을 모르면 화면이 고장 난 것처럼 보인다 */}
      {!inAggregate && (
        <span className="muted2" style={{ fontSize: 11.5, flexBasis: '100%' }}>
          아래 집계 목록에는 포함되지 않습니다 — 집계는 지원 조직 인원(billable 아님)을
          모집단에서 빼기 때문이며(C1-5), 개인 가동률은 그 규칙과 무관합니다.
        </span>
      )}
    </div>
  )
}

function round(value: number): number {
  return Math.round(value * 10) / 10
}
