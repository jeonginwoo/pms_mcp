/*
 * 상태 행위 한 줄 — 상태에 따라 **지금 할 수 있는 것 하나만** 보여 준다.
 *
 * - 계약대기 → `수주확정으로 →` (확인 카드)
 * - 수주확정 → `진행중으로 →` (확인 카드)
 * - 진행중 → `완료 처리`
 * - 완료 → `재개` + `유지보수로 이관 →`(PM만 — D1)
 * - 유지보수중 → 없음 (이관 뒤에는 유지보수 계약이 소관이다)
 *
 * 전이 버튼을 헤더에서 여기로 내린 이유(2026-08-22 사용자 결정): 상태를 옮기는 일은
 * 완료 처리·재개와 같은 계열이라 한 자리에 있어야 읽히고, 뱃지 옆에 붙이면 헤더가
 * 어수선해진다. 권한은 서버 규칙 그대로다 — 전이는 PM·PL, 완료·재개는 배정 전원.
 */
import { useState } from 'react'
import type { ReactNode } from 'react'
import { useStore } from '../store'
import { nextStatus } from '../labels'
import { canCompleteOrReopen, canEditInfo, canHandover } from '../permissions'
import { ErrorText } from './ui'
import StatusAdvance from './StatusAdvance'
import HandoverModal from './HandoverModal'
import type { MeView, ProjectDetail, ProjectPermissionMatrix } from '../types/api'

export default function ProjectActions() {
  const { detail, me, projectPermissions: permissions, complete, reopen, showToast } = useStore()
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)
  // 이관은 확인 카드가 아니라 폼이다 — 계약 필수 정보를 함께 받는다(D1-1)
  const [handingOver, setHandingOver] = useState(false)

  if (!detail) {
    return null
  }

  const runTransition = async (kind: 'complete' | 'reopen') => {
    setBusy(true)
    setError(null)
    const result = await (kind === 'complete' ? complete() : reopen())
    setBusy(false)

    if (result.ok) {
      showToast(kind === 'complete' ? '완료 처리되었습니다' : '재개되었습니다 — 진척률 90%')

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  return (
    <div style={{ borderTop: '1px solid var(--border-soft)', marginTop: 18, paddingTop: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        {actionFor(me, detail, permissions, busy, runTransition)}
        {/* 완료 상태에서는 재개와 이관이 둘 다 가능하다 — 유일하게 행위가 둘인 칸 */}
        {detail.status === 'COMPLETED' && canHandover(me, detail, permissions) && (
          <button className="btn btn-primary" disabled={busy}
            onClick={() => setHandingOver(true)}>유지보수로 이관 →</button>
        )}
        <span className="muted2" style={{ fontSize: 12 }}>{hintFor(me, detail, permissions)}</span>
      </div>

      {handingOver && (
        <HandoverModal detail={detail} onClose={() => setHandingOver(false)} />
      )}

      {error && (
        <div style={{ marginTop: 12 }}>
          <ErrorText code={error.code} message={error.message} />
        </div>
      )}
    </div>
  )
}

/** 상태마다 버튼은 하나다 — 지금 할 수 없는 행위는 아예 그리지 않는다. */
function actionFor(
    me: MeView | null,
    detail: ProjectDetail,
    permissions: ProjectPermissionMatrix | null,
    busy: boolean,
    run: (kind: 'complete' | 'reopen') => Promise<void>): ReactNode {
  if (detail.status === 'UNDER_MAINTENANCE') {
    return null
  }

  if (detail.status === 'COMPLETED') {
    return canCompleteOrReopen(me, detail, permissions) ? (
      <button className="btn btn-ghost" disabled={busy} onClick={() => void run('reopen')}>
        재개 (진척률 90%로 복귀)
      </button>
    ) : null
  }

  if (detail.status === 'IN_PROGRESS') {
    return canCompleteOrReopen(me, detail, permissions) ? (
      <button className="btn btn-primary" disabled={busy} onClick={() => void run('complete')}>
        완료 처리
      </button>
    ) : null
  }

  // 계약대기·수주확정 — 다음 한 칸으로 옮기는 것이 지금 할 일이다
  return canEditInfo(me, detail, permissions) ? <StatusAdvance /> : null
}

function hintFor(
    me: MeView | null,
    detail: ProjectDetail,
    permissions: ProjectPermissionMatrix | null): string {
  if (detail.status === 'UNDER_MAINTENANCE') {
    return '이관된 프로젝트입니다 — 상태는 유지보수 계약이 담당합니다.'
  }

  if (detail.status === 'COMPLETED') {
    return canCompleteOrReopen(me, detail, permissions)
      ? '재개하면 진척률이 90%로 돌아갑니다. 이관은 되돌릴 수 없습니다.'
      : '재개는 이 프로젝트에 배정된 인원만 가능합니다.'
  }

  if (detail.status === 'IN_PROGRESS') {
    return canCompleteOrReopen(me, detail, permissions)
      ? '진척률 100%에서만 완료 처리할 수 있습니다.'
      : '완료 처리는 이 프로젝트에 배정된 인원만 가능합니다.'
  }

  if (!canEditInfo(me, detail, permissions)) {
    return '상태 변경은 PM·PL만 가능합니다.'
  }

  return nextStatus(detail.status) === 'IN_PROGRESS'
    ? '진행중이 되면 진척률을 기록할 수 있습니다.'
    : '상태는 앞으로만 갑니다 — 되돌릴 수 없습니다.'
}
