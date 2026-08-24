/*
 * 통합 감사 로그 (AC G1-3 · 부록 A는 설정 화면의 탭으로 명세한다).
 *
 * **자리에 대한 메모**: 부록 A는 `/settings` 3탭(사용자 관리·조직 관리·감사 로그)인데
 * 이 앱은 사용자·조직을 `/people`("인력 · 조직")에 두고 있어, 감사만 별 항목으로
 * 얹었다(2026-08-24 사용자 결정). 이미 동작하는 두 화면을 재배치하는 것은 "비어 있는
 * 것을 채우는" 이번 작업의 성격을 벗어나기 때문이다 — 부록 A와의 차이는 미해결로 등재.
 *
 * **권한 판정은 서버가 한다**: 플래그가 없으면 `403 FORBIDDEN`이 오고 화면은 그것을
 * 그대로 보여 준다. 메뉴를 숨기는 것은 표시용일 뿐이라(상위 PRD §4-1) 이 화면에
 * 도달했을 때의 403 경로도 반드시 살아 있어야 한다.
 *
 * 조직·계정 변경까지 담는 **유일한 뷰**다 — 프로젝트 스코프 이력은 상세의 이력 탭이다.
 */
import { useCallback, useEffect, useState } from 'react'
import { useStore } from '../store'
import { Empty, ErrorText } from '../components/ui'
import AuditTable from '../components/AuditTable'
import type { AuditRecord } from '../types/api'

type Load =
  | { phase: 'loading' }
  | { phase: 'ready'; rows: AuditRecord[]; total: number; totalPages: number }
  | { phase: 'error'; code: string; message: string }

export default function Audit() {
  const { loadAudit, roster } = useStore()
  const [page, setPage] = useState(0)
  const [load, setLoad] = useState<Load>({ phase: 'loading' })

  const fetchRows = useCallback(async () => {
    setLoad({ phase: 'loading' })
    const result = await loadAudit(page)

    setLoad(result.ok
      ? {
        phase: 'ready',
        rows: result.value.content,
        total: result.value.totalElements,
        totalPages: result.value.totalPages,
      }
      : { phase: 'error', code: result.error.code, message: result.error.message })
  }, [loadAudit, page])

  useEffect(() => { void fetchRows() }, [fetchRows])

  // 감사 행은 actorId만 담는다 — 이름은 이미 받아 둔 명부에서 붙인다(추가 조회 없이)
  const nameOf = useCallback(
    (personId: number) =>
      roster.find((person) => person.id === personId)?.name ?? `#${personId}`,
    [roster])

  return (
    <section className="card">
      <div className="card-head">
        <h2>
          감사 로그{' '}
          {load.phase === 'ready' && (
            <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>
              {load.total}건
            </span>
          )}
        </h2>
        {load.phase === 'ready' && load.totalPages > 1 && (
          <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            <button className="btn btn-ghost btn-sm" disabled={page === 0}
              onClick={() => setPage((current) => current - 1)}>이전</button>
            <span className="muted2" style={{ fontSize: 12 }}>
              {page + 1} / {load.totalPages}
            </span>
            <button className="btn btn-ghost btn-sm" disabled={page + 1 >= load.totalPages}
              onClick={() => setPage((current) => current + 1)}>다음</button>
          </div>
        )}
      </div>

      {load.phase === 'loading' && <Empty>불러오는 중…</Empty>}

      {load.phase === 'error' && (
        <>
          <ErrorText code={load.code} message={load.message} />
          {load.code === 'FORBIDDEN' && (
            <div className="muted2" style={{ fontSize: 12, marginTop: 10 }}>
              통합 감사 로그는 &quot;사용자/조직/권한 관리&quot; 권한이 있는 그룹(기본: 관리자)만
              볼 수 있습니다 — 프로젝트 단위 이력은 각 프로젝트 상세의 이력 탭에 있습니다.
            </div>
          )}
        </>
      )}

      {load.phase === 'ready' && (
        <AuditTable rows={load.rows} showTarget nameOf={nameOf}
          empty="기록된 변경이 없습니다 — 시드 적재는 감사 행을 남기지 않습니다." />
      )}

      <div className="muted2" style={{ fontSize: 11.5, marginTop: 12 }}>
        모든 변경이 자동으로 남고 수정·삭제할 수 없습니다(append-only · G1-2). 최신순 정렬은
        서버가 정합니다 — 이력은 시간 순서가 의미의 일부입니다.
      </div>
    </section>
  )
}
