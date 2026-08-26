/*
 * 조직 트리 관리 (AC E3-1~E3-3) — 관리 권한자만 보는 패널.
 *
 * **신설은 노드마다 있는 [+ 하위]로 한다**(2026-08-24 — 부록 A가 요구한 구성이고 검수
 * 지적 항목이다). 구 화면은 상단 [+ 조직 추가] 하나에 상위 조직 셀렉트를 붙였는데, 만들
 * 자리를 보면서 고르는 편이 짧고 오조작이 적다 — 어느 부모 아래인지가 버튼의 위치로
 * 이미 정해지므로 셀렉트가 필요 없다. 회사(root) 아래에 만드는 것이 부문 신설이다.
 *
 * 표시 순서는 `orgTree.ts`가 정한다: 서버 응답에 정렬이 없어(`findAll()`) 들여쓰기만으로는
 * 새 노드가 목록 끝에 붙어 부모와 떨어져 보였다 — "트리 구조가 이상하다"는 지적이 이것이다.
 *
 * **이동(E3-5)**은 상위 조직을 골라 옮긴다. 셀렉트에서 자기 자신·자기 하위와 현재 부모를
 * 빼는 이유는 서버가 그것을 400으로 막기 때문이다(E3-6 순환 금지) — 고르고 나서 거절받는
 * 경로를 두면 사용자가 규칙을 오류 문구로 배운다. 회사(root)에는 이동 버튼이 없다.
 *
 * 삭제 가능 여부(`deletable`)는 서버가 판정해 준 값을 그대로 쓴다 — "빈 노드만 삭제"
 * 규칙을 화면이 다시 구현하지 않는다.
 *
 * 개명(E3-2, 2026-08-24 추가)은 **그 자리에서 고친다**: 이름 하나를 바꾸려고 모달을
 * 여는 것은 과하고, 소속 인원·프로젝트의 표시는 참조라 저절로 따라온다. 같은 부모
 * 밑의 동명은 서버가 막지 않는다 — AC에 없는 규칙이라 화면도 막지 않는다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { flattenOrgTree, orgOptionLabel, subtreeIds } from '../orgTree'
import { Empty, ErrorText } from './ui'
import type { OrgUnitView } from '../types/api'

export default function OrgUnitPanel() {
  const { orgUnits, createOrgUnit, renameOrgUnit, moveOrgUnitParent, deleteOrgUnit, showToast }
    = useStore()
  const [pending, setPending] = useState<number | null>(null)
  const [renaming, setRenaming] = useState<{ id: number; name: string } | null>(null)
  const [addingUnder, setAddingUnder] = useState<{ id: number; name: string } | null>(null)
  const [newName, setNewName] = useState('')
  const [moving, setMoving] = useState<{ id: number; name: string; parentId: string } | null>(null)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  // 표시 순서·깊이는 orgTree가 정한다 — 소속 선택 드롭다운과 같은 규칙을 쓴다
  const rows = flattenOrgTree(orgUnits)

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
    if (!addingUnder || newName.trim() === '') {
      setError({ code: 'VALIDATION_ERROR', message: '조직명을 입력해 주세요' })

      return
    }

    setBusy(true)
    const result = await createOrgUnit({ parentId: addingUnder.id, name: newName.trim() })
    setBusy(false)

    if (result.ok) {
      setError(null)
      setAddingUnder(null)
      setNewName('')
      showToast(`${addingUnder.name} 아래에 ${result.value.name}을(를) 추가했습니다`)

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  const runMove = async () => {
    if (!moving || moving.parentId === '') {
      setError({ code: 'VALIDATION_ERROR', message: '옮길 상위 조직을 선택해 주세요' })

      return
    }

    setBusy(true)
    const result = await moveOrgUnitParent(moving.id, Number(moving.parentId))
    setBusy(false)

    if (result.ok) {
      setError(null)
      setMoving(null)
      showToast(`${moving.name}을(를) 옮겼습니다`)

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  /** 노드 뒤에 붙는 요약 — 회사는 그 사실만, 그 아래는 하위·인원·프로젝트 수를 센다. */
  const metaOf = (unit: OrgUnitView, depth: number): string => {
    if (depth === 0) {
      return '회사'
    }

    const parts: string[] = []

    if (unit.childCount > 0) {
      parts.push('하위 ' + unit.childCount)
    }

    parts.push('인원 ' + unit.memberCount)
    // 프로젝트는 그 노드가 PM 소속 노드인 것만 센다(직속 · 부록 A). 0건이면 적지
    // 않는다 — 인원과 달리 대부분의 노드가 0이라 매 줄에 "프로젝트 0"이 붙는다
    if (unit.projectCount > 0) {
      parts.push('프로젝트 ' + unit.projectCount)
    }

    return parts.join(' · ')
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
      </div>
      <div className="muted2" style={{ fontSize: 11.5, marginBottom: 10 }}>
        원하는 깊이까지 추가할 수 있습니다. 이름을 바꾸면 소속 인원·프로젝트에 함께
        반영되고, 인원·하위 조직이 있으면 삭제할 수 없습니다.
      </div>

      {rows.map(({ unit, depth }) => (
        <div key={unit.id}>
          {/* 오른쪽 열 214px = 버튼 4종(+하위·개명·이동·삭제)이 한 줄에 들어가는 폭 */}
          <div className="trow"
            style={{ gridTemplateColumns: 'minmax(0,1fr) 214px', gap: 6, padding: '8px 2px' }}>
            <div style={{ paddingLeft: depth * 14, minWidth: 0 }}>
              {renaming?.id === unit.id ? (
                <input value={renaming.name} autoFocus disabled={busy}
                  onChange={(e) => setRenaming({ id: unit.id, name: e.target.value })}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') { void runRename() }
                    if (e.key === 'Escape') { setRenaming(null) }
                  }}
                  style={{ fontSize: 12.5, padding: '4px 7px', borderRadius: 6, width: '100%' }} />
              ) : (
                <span style={{ minWidth: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  <span style={{ fontWeight: depth === 0 ? 700 : 600 }}>{unit.name}</span>
                  <span className="muted2" style={{ fontSize: 11.5, marginLeft: 6 }}>
                    {metaOf(unit, depth)}
                  </span>
                </span>
              )}
            </div>
            {renaming?.id === unit.id ? (
              <span style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                <button className="btn btn-primary btn-sm" disabled={busy}
                  onClick={() => void runRename()}>저장</button>
                <button className="btn btn-ghost btn-sm" onClick={() => setRenaming(null)}>취소</button>
              </span>
            ) : pending === unit.id ? (
              <span style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                <button className="btn btn-danger btn-sm"
                  onClick={() => void runDelete(unit.id, unit.name)}>확인</button>
                <button className="btn btn-ghost btn-sm" onClick={() => setPending(null)}>취소</button>
              </span>
            ) : (
              <span style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                <button className="btn btn-ghost btn-sm" title="이 조직 아래에 신설 (E3-1)"
                  onClick={() => { setAddingUnder({ id: unit.id, name: unit.name }); setNewName(''); setError(null) }}>
                  + 하위
                </button>
                <button className="btn btn-ghost btn-sm" title="이름 변경 (E3-2)"
                  onClick={() => { setRenaming({ id: unit.id, name: unit.name }); setError(null) }}>
                  개명
                </button>
                {/* 회사(root)는 옮길 수 없다 — 부문 가시성이 root 직계 자식 기준이다(E3-6) */}
                {depth > 0 && (
                  <button className="btn btn-ghost btn-sm" title="상위 조직 변경 (E3-5)"
                    onClick={() => {
                      setMoving({ id: unit.id, name: unit.name, parentId: '' })
                      setError(null)
                    }}>
                    이동
                  </button>
                )}
                {/* 회사(root)에는 삭제를 두지 않는다 — 서버도 deletable=false로 답한다 */}
                {depth > 0 && (
                  <button className="btn btn-danger-ghost btn-sm" disabled={!unit.deletable}
                    title={unit.deletable
                      ? '빈 노드 삭제'
                      : `소속 인원 ${unit.memberCount}명 · 프로젝트 ${unit.projectCount}건 · `
                        + `하위 조직 ${unit.childCount}개가 있어 삭제할 수 없습니다`}
                    style={{ opacity: unit.deletable ? 1 : .35 }}
                    onClick={() => { setPending(unit.id); setError(null) }}>
                    삭제
                  </button>
                )}
              </span>
            )}
          </div>

          {moving?.id === unit.id && (
            <div style={{ display: 'flex', gap: 6, margin: '2px 0 8px', marginLeft: depth * 14 + 14, background: 'var(--soft)', border: '1px solid var(--border-soft)', borderRadius: 10, padding: 8 }}>
              <select value={moving.parentId} disabled={busy} autoFocus
                onChange={(e) => setMoving({ ...moving, parentId: e.target.value })}
                style={{ flex: 1, fontSize: 12.5, padding: '6px 8px', borderRadius: 7 }}>
                <option value="">옮길 상위 조직 선택…</option>
                {/* 자기 자신·자기 하위와 현재 부모는 고를 수 없다 — 앞의 둘은 서버가
                    400으로 막는 순환이고(E3-6), 현재 부모는 바뀌는 것이 없다 */}
                {rows.filter(({ unit: candidate }) => !subtreeIds(orgUnits, unit.id)
                  .has(candidate.id) && candidate.id !== unit.parentId)
                  .map((row) => (
                    <option key={row.unit.id} value={row.unit.id}>{orgOptionLabel(row)}</option>
                  ))}
              </select>
              <button className="btn btn-primary btn-sm" disabled={busy}
                onClick={() => void runMove()}>이동</button>
              <button className="btn btn-ghost btn-sm" onClick={() => setMoving(null)}>취소</button>
            </div>
          )}

          {addingUnder?.id === unit.id && (
            <div style={{ display: 'flex', gap: 6, margin: '2px 0 8px', marginLeft: depth * 14 + 14, background: 'var(--soft)', border: '1px solid var(--border-soft)', borderRadius: 10, padding: 8 }}>
              <input value={newName} autoFocus placeholder="새 조직명" disabled={busy}
                onChange={(e) => setNewName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') { void runCreate() }
                  if (e.key === 'Escape') { setAddingUnder(null) }
                }}
                style={{ flex: 1, fontSize: 12.5, padding: '6px 8px', borderRadius: 7 }} />
              <button className="btn btn-primary btn-sm" disabled={busy}
                onClick={() => void runCreate()}>추가</button>
              <button className="btn btn-ghost btn-sm" onClick={() => setAddingUnder(null)}>취소</button>
            </div>
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
