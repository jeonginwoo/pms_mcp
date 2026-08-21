/*
 * 상태 전이 버튼 (AC A5-1) — 상세 화면에서 한 칸씩 앞으로만.
 *
 * 정보 수정 폼 안에 두지 않는 이유(2026-08-22 사용자 결정): 상태 전이는 "정보 고치기"와
 * 다른 행위다 — 되돌릴 수 없고(역방향 금지 §5), 조회 화면에서 바로 눌러야 하는 흐름이다.
 * 그래서 폼에서 status를 빼고 이 버튼 하나로 모았다. 확인 카드를 두는 이유도 같다:
 * 실수로 넘긴 단계는 이 경로로 되돌릴 수 없다.
 *
 * 계약대기 → 수주확정 → 진행중까지만 나온다 — 완료·재개는 전용 버튼, 이관은 유지보수
 * 경로의 몫이다(서버 `ProjectStatus.next()`가 같은 표를 갖고 그 외는 409로 막는다).
 */
import { useState } from 'react'
import { useStore } from '../store'
import { STATUS_LABEL, nextStatus } from '../labels'
import { editBodyOf } from '../projectBody'
import { ErrorText } from './ui'

export default function StatusAdvance() {
  const { detail, editProject, showToast } = useStore()
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  if (!detail) {
    return null
  }

  const next = nextStatus(detail.status)

  if (!next) {
    return null
  }

  const advance = async () => {
    setBusy(true)
    setError(null)
    const result = await editProject(editBodyOf(detail, { status: next }))
    setBusy(false)

    if (result.ok) {
      setConfirming(false)
      showToast(`${STATUS_LABEL[next]}으로 변경되었습니다`)

      return
    }

    // 실패하면 확인 카드를 닫지 않는다 — 문구를 보여 줄 자리가 여기뿐이다
    setError({ code: result.error.code, message: result.error.message })
  }

  return (
    <>
      <button className="btn btn-primary"
        disabled={busy} onClick={() => { setConfirming(true); setError(null) }}>
        {STATUS_LABEL[next]}으로 →
      </button>

      {confirming && (
        <div className="overlay" onMouseDown={(e) => {
          if (e.target === e.currentTarget) {
            setConfirming(false)
          }
        }}>
          <div className="modal" style={{ width: 420 }}>
            <h3>상태를 변경하시겠습니까?</h3>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '16px 0 10px' }}>
              <span className="badge" style={{ background: 'var(--chip)', color: 'var(--muted)' }}>
                {STATUS_LABEL[detail.status]}
              </span>
              <span className="muted2">→</span>
              <span className="badge" style={{ background: 'var(--primary-soft)', color: 'var(--primary)' }}>
                {STATUS_LABEL[next]}
              </span>
            </div>
            <div className="muted" style={{ fontSize: 12.5, marginBottom: 14 }}>
              {detail.name} · 되돌릴 수 없습니다 — 상태는 앞으로만 갑니다(역방향 금지).
              {next === 'IN_PROGRESS' && ' 진행중이 되면 진척률을 수정할 수 있습니다.'}
            </div>

            {error && <ErrorText code={error.code} message={error.message} />}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}>
              <button className="btn btn-ghost" onClick={() => setConfirming(false)}>취소</button>
              <button className="btn btn-primary" disabled={busy} onClick={() => void advance()}>
                {busy ? '변경 중…' : `${STATUS_LABEL[next]}으로 변경`}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
