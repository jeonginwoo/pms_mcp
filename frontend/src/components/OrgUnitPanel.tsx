/*
 * 조직 트리 관리 (AC E3-3) — 관리 권한자만 보는 패널.
 *
 * 삭제 가능 여부(`deletable`)는 서버가 판정해 준 값을 그대로 쓴다 — "빈 노드만 삭제"
 * 규칙을 화면이 다시 구현하지 않는다. 신설(E3-1)은 상위 조직을 골라 만든다 —
 * 회사(root)는 하나뿐이라 상위 없는 생성은 서버가 거절한다.
 *
 * 개명(E3-2, 2026-08-24 추가)은 **그 자리에서 고친다**: 이름 하나를 바꾸려고 모달을
 * 여는 것은 과하고, 소속 인원·프로젝트의 표시는 참조라 저절로 따라온다. 같은 부모
 * 밑의 동명은 서버가 막지 않는다 — AC에 없는 규칙이라 화면도 막지 않는다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { Empty, ErrorText } from './ui'

export default function OrgUnitPanel() {
  const { orgUnits, createOrgUnit, renameOrgUnit, deleteOrgUnit, showToast } = useStore()
  const [pending, setPending] = useState<number | null>(null)
  const [renaming, setRenaming] = useState<{ id: number; name: string } | null>(null)
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ parentId: '', name: '' })
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  /** 트리 들여쓰기 깊이 — 노드 수십 개 규모라 부모를 따라 올라가며 센다. */
  const depthOf = (parentId: number | null): number => {
    let depth = 0
    let current = parentId

    while (current !== null) {
      depth += 1
      current = orgUnits.find((unit) => unit.id === current)?.parentId ?? null
    }

    return depth
  }

  const runDelete = async (orgUnitId: number, name: string) => {
    setBusy(true)
    const result = await deleteOrgUnit(orgUnitId)
    setBusy(false)
    setPending(null)

    if (result.ok) {
      setError(null)
      showToast(`${name}을(를) 삭제했습니다`)

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  const runRename = async () => {
    if (!renaming || renaming.name.trim() === '') {
      return
    }

    setBusy(true)
    const result = await renameOrgUnit(renaming.id, renaming.name.trim())
    setBusy(false)

    if (result.ok) {
      setError(null)
      setRenaming(null)
      showToast(`${result.value.name}(으)로 이름을 바꿨습니다`)

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  const runCreate = async () => {
    if (!form.parentId) {
      setError({ code: 'VALIDATION_ERROR', message: '상위 조직을 선택해 주세요' })

      return
    }

    setBusy(true)
    const result = await createOrgUnit({ parentId: Number(form.parentId), name: form.name.trim() })
    setBusy(false)

    if (result.ok) {
      setError(null)
      setAdding(false)
      setForm({ parentId: '', name: '' })
      showToast(`${result.value.name}을(를) 추가했습니다`)

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  return (
    <section className="card">
      <div className="card-head">
        <h3>
          조직{' '}
          <span className="muted2" style={{ fontWeight: 500, fontSize: 12 }}>
            {orgUnits.length}개 노드
          </span>
        </h3>
        <button className="btn btn-ghost btn-sm" onClick={() => { setAdding(true); setError(null) }}>
          + 조직 추가
        </button>
      </div>

      {adding && (
        <div style={{ display: 'grid', gap: 7, marginBottom: 12, background: 'var(--soft)', border: '1px solid var(--border-soft)', borderRadius: 10, padding: 10 }}>
          <select value={form.parentId} disabled={busy}
            onChange={(e) => setForm((current) => ({ ...current, parentId: e.target.value }))}
            style={{ fontSize: 12.5, padding: '6px 8px', borderRadius: 7 }}>
            <option value="">상위 조직 선택…</option>
            {orgUnits.map((unit) => (
              <option key={unit.id} value={unit.id}>{unit.name}</option>
            ))}
          </select>
          <div style={{ display: 'flex', gap: 6 }}>
            <input value={form.name} placeholder="조직명" disabled={busy}
              onChange={(e) => setForm((current) => ({ ...current, name: e.target.value }))}
              onKeyDown={(e) => { if (e.key === 'Enter') { void runCreate() } }}
              style={{ flex: 1, fontSize: 12.5, padding: '6px 8px', borderRadius: 7 }} />
            <button className="btn btn-primary btn-sm" disabled={busy}
              onClick={() => void runCreate()}>추가</button>
            <button className="btn btn-ghost btn-sm" onClick={() => setAdding(false)}>취소</button>
          </div>
        </div>
      )}

      {orgUnits.map((unit) => (
        <div key={unit.id} className="trow"
          style={{ gridTemplateColumns: 'minmax(0,1fr) 46px 96px', gap: 6, padding: '8px 2px' }}>
          {renaming?.id === unit.id ? (
            <input value={renaming.name} autoFocus disabled={busy}
              onChange={(e) => setRenaming({ id: unit.id, name: e.target.value })}
              onKeyDown={(e) => {
                if (e.key === 'Enter') { void runRename() }
                if (e.key === 'Escape') { setRenaming(null) }
              }}
              style={{ marginLeft: depthOf(unit.parentId) * 12, fontSize: 12.5, padding: '4px 7px', borderRadius: 6 }} />
          ) : (
            <span style={{ paddingLeft: depthOf(unit.parentId) * 12, minWidth: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {unit.name}
            </span>
          )}
          <span className="muted2" style={{ fontSize: 11.5, textAlign: 'right' }}>
            {unit.memberCount}명
          </span>
          {renaming?.id === unit.id ? (
            <span style={{ display: 'flex', gap: 4 }}>
              <button className="btn btn-primary btn-sm" disabled={busy}
                onClick={() => void runRename()}>저장</button>
              <button className="btn btn-ghost btn-sm" onClick={() => setRenaming(null)}>취소</button>
            </span>
          ) : pending === unit.id ? (
            <span style={{ display: 'flex', gap: 4 }}>
              <button className="btn btn-danger btn-sm"
                onClick={() => void runDelete(unit.id, unit.name)}>확인</button>
              <button className="btn btn-ghost btn-sm" onClick={() => setPending(null)}>취소</button>
            </span>
          ) : (
            <span style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
              <button className="btn btn-ghost btn-sm" title="이름 변경 (E3-2)"
                onClick={() => { setRenaming({ id: unit.id, name: unit.name }); setError(null) }}>
                개명
              </button>
              <button className="btn btn-danger-ghost btn-sm" disabled={!unit.deletable}
                title={unit.deletable
                  ? '빈 노드 삭제'
                  : `소속 인원 ${unit.memberCount}명 · 하위 조직 ${unit.childCount}개가 있어 삭제할 수 없습니다`}
                style={{ opacity: unit.deletable ? 1 : .35 }}
                onClick={() => { setPending(unit.id); setError(null) }}>
                삭제
              </button>
            </span>
          )}
        </div>
      ))}

      {orgUnits.length === 0 && <Empty>조직 노드가 없습니다.</Empty>}

      {error && (
        <div style={{ marginTop: 12 }}>
          <ErrorText code={error.code} message={error.message} />
        </div>
      )}

      <div className="muted2" style={{ fontSize: 11.5, marginTop: 12 }}>
        소속 인원이나 하위 조직이 있으면 삭제할 수 없습니다 (409 IN_USE) — 인원을 옮기거나
        비활성한 뒤 삭제하세요.
      </div>
    </section>
  )
}
