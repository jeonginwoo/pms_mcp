/*
 * 직급 관리 (US-E4) — 관리 권한자만 보는 패널.
 *
 * 계수(coeff)가 이 화면의 핵심이다: **보정 가동률의 가중치**이고 캐시가 없어
 * 바꾸면 다음 조회부터 바로 반영된다(E4-2). 그래서 계수를 고치는 것은 조용한 변경이
 * 아니라 대시보드 수치를 움직이는 일이라는 것을 화면이 말해 준다.
 *
 * 삭제는 **쓰는 인원이 0명일 때만** 버튼이 열린다(E4-3). 그 판정 기준은 서버가 준
 * `memberCount`이고, 그것은 비활성 인원까지 센 값이다 — 서버의 409 IN_USE와 같은
 * 기준이라 "0명인데 거절당하는" 어긋남이 생기지 않는다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { Empty, ErrorText } from './ui'
import type { GradeDetail } from '../types/api'

interface Draft {
  /** null이면 신규 등록 */
  id: number | null
  name: string
  coeff: string
  version: number
}

export default function GradePanel() {
  const { grades, createGrade, updateGrade, deleteGrade, showToast } = useStore()
  const [draft, setDraft] = useState<Draft | null>(null)
  const [pending, setPending] = useState<number | null>(null)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const save = async () => {
    if (!draft) {
      return
    }

    const coeff = Number(draft.coeff)

    if (!(coeff > 0)) {
      setError({ code: 'VALIDATION_ERROR', message: '계수는 0보다 커야 합니다' })

      return
    }

    setBusy(true)
    const body = { name: draft.name.trim(), coeff, version: draft.version }
    const result = draft.id === null
      ? await createGrade(body)
      : await updateGrade(draft.id, body)
    setBusy(false)

    if (result.ok) {
      setError(null)
      setDraft(null)
      showToast(`${result.value.name} 직급을 저장했습니다`)

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  const remove = async (grade: GradeDetail) => {
    setBusy(true)
    const result = await deleteGrade(grade.id)
    setBusy(false)
    setPending(null)

    if (result.ok) {
      setError(null)
      showToast(`${grade.name} 직급을 삭제했습니다`)

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  return (
    <section className="card">
      <div className="card-head">
        <h3>
          직급{' '}
          <span className="muted2" style={{ fontWeight: 500, fontSize: 12 }}>
            {grades.length}개
          </span>
        </h3>
        <button className="btn btn-ghost btn-sm"
          onClick={() => { setDraft({ id: null, name: '', coeff: '1.0', version: 0 }); setError(null) }}>
          + 직급 추가
        </button>
      </div>

      {draft && (
        <div style={{ display: 'flex', gap: 6, marginBottom: 12, background: 'var(--soft)', border: '1px solid var(--border-soft)', borderRadius: 10, padding: 10 }}>
          <input value={draft.name} placeholder="직급명" autoFocus disabled={busy}
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
            style={{ flex: 1, fontSize: 12.5, padding: '6px 8px', borderRadius: 7 }} />
          <input value={draft.coeff} placeholder="계수" type="number" step="0.1" min="0.1"
            disabled={busy}
            onChange={(e) => setDraft({ ...draft, coeff: e.target.value })}
            style={{ width: 76, fontSize: 12.5, padding: '6px 8px', borderRadius: 7 }} />
          <button className="btn btn-primary btn-sm" disabled={busy} onClick={() => void save()}>
            저장
          </button>
          <button className="btn btn-ghost btn-sm" onClick={() => setDraft(null)}>취소</button>
        </div>
      )}

      {grades.map((grade) => (
        <div key={grade.id} className="trow"
          style={{ gridTemplateColumns: 'minmax(0,1fr) 46px 46px 96px', gap: 6, padding: '8px 2px' }}>
          <span style={{ minWidth: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {grade.name}
          </span>
          <span className="muted2" style={{ fontSize: 11.5, textAlign: 'right' }}>
            ×{grade.coeff}
          </span>
          <span className="muted2" style={{ fontSize: 11.5, textAlign: 'right' }}>
            {grade.memberCount}명
          </span>
          {pending === grade.id ? (
            <span style={{ display: 'flex', gap: 4 }}>
              <button className="btn btn-danger btn-sm" disabled={busy}
                onClick={() => void remove(grade)}>확인</button>
              <button className="btn btn-ghost btn-sm" onClick={() => setPending(null)}>취소</button>
            </span>
          ) : (
            <span style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
              <button className="btn btn-ghost btn-sm" title="이름·계수 수정 (E4-2)"
                onClick={() => {
                  setDraft({
                    id: grade.id,
                    name: grade.name,
                    coeff: String(grade.coeff),
                    version: grade.version,
                  })
                  setError(null)
                }}>
                수정
              </button>
              <button className="btn btn-danger-ghost btn-sm" disabled={grade.memberCount > 0}
                title={grade.memberCount > 0
                  ? `이 직급을 쓰는 인원 ${grade.memberCount}명이 있어 삭제할 수 없습니다`
                  : '삭제'}
                style={{ opacity: grade.memberCount > 0 ? .35 : 1 }}
                onClick={() => { setPending(grade.id); setError(null) }}>
                삭제
              </button>
            </span>
          )}
        </div>
      ))}

      {grades.length === 0 && <Empty>직급이 없습니다.</Empty>}

      {error && (
        <div style={{ marginTop: 12 }}>
          <ErrorText code={error.code} message={error.message} />
        </div>
      )}

      <div className="muted2" style={{ fontSize: 11.5, marginTop: 12 }}>
        계수는 <strong>보정 가동률</strong>의 가중치입니다 — 바꾸면 다음 조회부터 대시보드
        수치가 달라집니다(캐시 없음). 쓰는 인원이 있으면 삭제할 수 없습니다(409 IN_USE).
      </div>
    </section>
  )
}
