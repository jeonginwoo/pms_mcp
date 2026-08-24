/*
 * 프로젝트 이력 탭 (AC G2-2 · 부록 A "이력 탭") — lastEditedBy/At의 상세판이다.
 *
 * **가시성 안이면 역할과 무관하게 보인다**(G2-3, 2026-08-06 확정): 참여자도 볼 수 있다.
 * "이력의 대상 데이터를 볼 수 있는 사람은 그 변경 사실도 본다"가 규칙이고, 그래서
 * 이 패널은 권한 조건 없이 상세 화면과 함께 열린다 — 가시성 밖이면 애초에 상세가
 * 404였다. 서버가 여기서 또 404를 주면 그것도 은닉이지 오류가 아니다.
 *
 * 통합 로그(G1-3)와 **같은 행**을 `projectId` 필터로 볼 뿐이므로 표는 공유한다.
 */
import { useCallback, useEffect, useState } from 'react'
import { useStore } from '../store'
import { Empty, ErrorText } from './ui'
import AuditTable from './AuditTable'
import type { AuditRecord } from '../types/api'

type Load =
  | { phase: 'loading' }
  | { phase: 'ready'; rows: AuditRecord[]; total: number; totalPages: number }
  | { phase: 'error'; code: string; message: string }

export default function ProjectAuditPanel({ projectId }: { projectId: number }) {
  const { loadProjectAudit, roster } = useStore()
  const [page, setPage] = useState(0)
  const [load, setLoad] = useState<Load>({ phase: 'loading' })

  // 프로젝트가 바뀌면 첫 페이지로 — 3페이지를 보던 중 다른 프로젝트를 열면 빈 화면이 된다
  useEffect(() => { setPage(0) }, [projectId])

  const fetchRows = useCallback(async () => {
    setLoad({ phase: 'loading' })
    const result = await loadProjectAudit(projectId, page)

    setLoad(result.ok
      ? {
        phase: 'ready',
        rows: result.value.content,
        total: result.value.totalElements,
        totalPages: result.value.totalPages,
      }
      : { phase: 'error', code: result.error.code, message: result.error.message })
  }, [loadProjectAudit, projectId, page])

  useEffect(() => { void fetchRows() }, [fetchRows])

  const nameOf = useCallback(
    (personId: number) =>
      roster.find((person) => person.id === personId)?.name ?? `#${personId}`,
    [roster])

  return (
    <section className="card">
      <div className="card-head">
        <h3>
          변경 이력{' '}
          {load.phase === 'ready' && (
            <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>
              {load.total}건
            </span>
          )}
        </h3>
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
      {load.phase === 'error' && <ErrorText code={load.code} message={load.message} />}

      {load.phase === 'ready' && (
        <AuditTable rows={load.rows} showTarget={false} nameOf={nameOf}
          empty="이 프로젝트의 변경 이력이 없습니다 — 시드 적재는 감사 행을 남기지 않습니다." />
      )}
    </section>
  )
}
