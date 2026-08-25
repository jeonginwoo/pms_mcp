/*
 * 프로젝트 권한 패널 (US-A8 · 부록 A 상세 화면) — 역할×기능 토글 매트릭스.
 *
 * **표를 화면이 만들지 않는다**: 어떤 칸이 있는지·기본값이 무엇인지·무엇이 고정인지는
 * 전부 서버 응답(`cells`)이 말한다. §4-2를 여기 다시 적으면 override가 걸린 프로젝트에서
 * 화면과 서버의 답이 갈린다 — 그것이 A8이 만드는 바로 그 차이다.
 *
 * 저장은 **전체 교체**라 지금 화면의 조정 가능한 칸 전부를 보낸다(A8-2). 서버가 기본값과
 * 같은 칸을 버리므로 "기본값 복원"은 빈 목록 하나로 끝난다 — 별도 API가 없는 이유다.
 */
import { useEffect, useState } from 'react'
import { useStore } from '../store'
import { ACTION_LABEL, ROLE_LABEL } from '../labels'
import type { PermissionCell, ProjectAction, ProjectRole } from '../types/api'

/** 화면에 그리는 축 — 서버가 보낸 칸에서 뽑는다(순서는 서버 열거 순서 그대로) */
function axesOf(cells: PermissionCell[]) {
  const roles: ProjectRole[] = []
  const actions: ProjectAction[] = []

  for (const cell of cells) {
    if (!roles.includes(cell.role)) {
      roles.push(cell.role)
    }

    if (!actions.includes(cell.action)) {
      actions.push(cell.action)
    }
  }

  return { roles, actions }
}

function keyOf(role: ProjectRole, action: ProjectAction): string {
  return `${role}.${action}`
}

export default function PermissionPanel() {
  const { projectPermissions, savePermissions, showToast } = useStore()
  const [draft, setDraft] = useState<Record<string, boolean>>({})
  const [saving, setSaving] = useState(false)

  // 서버 응답이 바뀌면(열람·저장) 초안을 그것으로 되맞춘다 — 저장 결과가 정본이다
  useEffect(() => {
    if (!projectPermissions) {
      return
    }

    const next: Record<string, boolean> = {}
    for (const cell of projectPermissions.cells) {
      next[keyOf(cell.role, cell.action)] = cell.allowed
    }
    setDraft(next)
  }, [projectPermissions])

  if (!projectPermissions) {
    return null
  }

  const { roles, actions } = axesOf(projectPermissions.cells)
  const cellOf = (role: ProjectRole, action: ProjectAction) =>
    projectPermissions.cells.find((c) => c.role === role && c.action === action)

  const dirty = projectPermissions.cells.some(
    (cell) => cell.editable && draft[keyOf(cell.role, cell.action)] !== cell.allowed)
  const hasOverride = projectPermissions.cells.some((cell) => cell.overridden)

  // 조정 가능한 칸 전부를 보낸다 — 서버가 기본값과 같은 것을 버린다(A8-2)
  const submit = async (overrides: { role: ProjectRole; action: ProjectAction;
      allowed: boolean }[]) => {
    setSaving(true)
    const result = await savePermissions(overrides)
    setSaving(false)

    if (!result.ok) {
      showToast(`${result.error.message} (${result.error.code})`)
    }
  }

  const save = () => submit(projectPermissions.cells
    .filter((cell) => cell.editable)
    .map((cell) => ({
      role: cell.role,
      action: cell.action,
      allowed: draft[keyOf(cell.role, cell.action)] ?? cell.allowed,
    })))

  return (
    <section style={{ marginTop: 18 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
        <h3 style={{ fontSize: 14, fontWeight: 700, margin: 0 }}>역할별 권한</h3>
        <span className="muted2" style={{ fontSize: 11.5 }}>
          기본값은 전사 규칙이고, 이 프로젝트에서만 조정합니다. 잠금은 바꿀 수 없는 칸입니다.
        </span>
      </div>

      <div style={{ overflowX: 'auto' }}>
        <table style={{ borderCollapse: 'collapse', fontSize: 12.5, minWidth: 420 }}>
          <thead>
            <tr>
              <th style={{ textAlign: 'left', padding: '6px 10px 6px 0' }} />
              {roles.map((role) => (
                <th key={role} style={{ padding: '6px 10px', fontWeight: 700 }}>
                  {ROLE_LABEL[role]}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {actions.map((action) => (
              <tr key={action}>
                <td className="muted" style={{ padding: '6px 10px 6px 0', whiteSpace: 'nowrap' }}>
                  {ACTION_LABEL[action]}
                </td>
                {roles.map((role) => {
                  const cell = cellOf(role, action)

                  if (!cell) {
                    return <td key={role} style={{ padding: '6px 10px' }}>—</td>
                  }

                  return (
                    <td key={role} style={{ padding: '6px 10px', textAlign: 'center' }}>
                      <label style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                        <input
                          type="checkbox"
                          disabled={!cell.editable || saving}
                          checked={draft[keyOf(role, action)] ?? cell.allowed}
                          onChange={(e) => setDraft((prev) => ({
                            ...prev, [keyOf(role, action)]: e.target.checked }))} />
                        {/* 고정 칸은 왜 못 바꾸는지 그 자리에서 말해 준다 (§4-2) */}
                        {!cell.editable && (
                          <span className="muted2" style={{ fontSize: 10.5 }} title="고정 칸입니다">
                            잠금
                          </span>
                        )}
                        {cell.overridden && (
                          <span style={{ fontSize: 10.5, color: 'var(--primary)' }}>커스텀</span>
                        )}
                      </label>
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
        <button className="btn btn-primary" disabled={!dirty || saving} onClick={() => void save()}>
          {saving ? '저장 중…' : '저장'}
        </button>
        {/* 빈 목록이 곧 전체 기본값 복원이다 (A8-2 — 별도 API가 없다) */}
        <button className="btn" disabled={!hasOverride || saving} onClick={() => void submit([])}>
          기본값 복원
        </button>
      </div>
    </section>
  )
}
