/*
 * 권한 그룹 관리 (US-E5) — 관리 권한자만 보는 패널.
 *
 * 부록 A가 그린 행 그대로다: 이름 · n명 · [권한 ▾] 펼침(가시성 select + 기능 토글) ·
 * [수정] · 인원 0일 때만 [삭제], 관리자 그룹은 버튼 비활성.
 *
 * **`systemFixed`(관리자) 그룹의 버튼을 잠그는 것이 이 화면에서 가장 중요한 규칙이다**:
 * 서버는 422 IMMUTABLE_GROUP으로 막지만, 화면이 누를 수 있게 두면 마지막 관리자가
 * 자기 권한을 지우려다 오류를 보고서야 그 사실을 알게 된다. 왜 잠겼는지도 함께 말한다.
 *
 * 가시성 scope와 기능 플래그를 한 폼에서 다루는 이유는 둘이 같은 그룹 정의의 두 면이고
 * (상위 PRD §4-3) 서버 PUT이 전체 치환이기 때문이다 — 따로 저장하면 한쪽이 다른 쪽을
 * 덮어쓴다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { VISIBILITY_SCOPE_LABEL, VISIBILITY_SCOPE_ORDER } from '../labels'
import { Empty, ErrorText } from './ui'
import type { PermissionGroupBody, PermissionGroupDetail, VisibilityScope } from '../types/api'

/** 기능 플래그 4종 (상위 PRD §4-3) — 서버 필드명 그대로다 */
const FLAGS: { key: keyof PermissionGroupBody & string; label: string; hint: string }[] = [
  { key: 'createProject', label: '프로젝트 생성', hint: '팀장 이상 — 과부하 알림 수신자 판정에도 쓰인다' },
  { key: 'manageContracts', label: '계약 관리', hint: '유지보수 계약·사이트 등록/수정' },
  { key: 'manageAllProjects', label: '전 프로젝트 관리', hint: '모든 프로젝트에서 PM으로 간주' },
  { key: 'manageOrg', label: '사용자/조직/권한 관리', hint: '이 화면을 여는 권한' },
]

type Draft = PermissionGroupBody & { id: number | null }

const NEW_GROUP: Draft = {
  id: null,
  name: '',
  visibilityScope: 'TEAM',
  createProject: false,
  manageContracts: false,
  manageAllProjects: false,
  manageOrg: false,
  version: 0,
}

export default function PermissionGroupPanel() {
  const {
    permissionGroups,
    createPermissionGroup,
    updatePermissionGroup,
    deletePermissionGroup,
    showToast,
  } = useStore()
  const [draft, setDraft] = useState<Draft | null>(null)
  const [expanded, setExpanded] = useState<number | null>(null)
  const [pending, setPending] = useState<number | null>(null)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const save = async () => {
    if (!draft || draft.name.trim() === '') {
      setError({ code: 'VALIDATION_ERROR', message: '그룹명을 입력해 주세요' })

      return
    }

    setBusy(true)
    const { id, ...body } = draft
    const result = id === null
      ? await createPermissionGroup({ ...body, name: body.name.trim() })
      : await updatePermissionGroup(id, { ...body, name: body.name.trim() })
    setBusy(false)

    if (result.ok) {
      setError(null)
      setDraft(null)
      showToast(`${result.value.name} 그룹을 저장했습니다`)

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  const remove = async (group: PermissionGroupDetail) => {
    setBusy(true)
    const result = await deletePermissionGroup(group.id)
    setBusy(false)
    setPending(null)

    if (result.ok) {
      setError(null)
      showToast(`${group.name} 그룹을 삭제했습니다`)

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  return (
    <section className="card">
      <div className="card-head">
        <h3>
          권한 그룹{' '}
          <span className="muted2" style={{ fontWeight: 500, fontSize: 12 }}>
            {permissionGroups.length}개
          </span>
        </h3>
        <button className="btn btn-ghost btn-sm"
          onClick={() => { setDraft({ ...NEW_GROUP }); setError(null) }}>
          + 그룹 추가
        </button>
      </div>

      {draft && <GroupForm draft={draft} busy={busy} onChange={setDraft}
        onSave={() => void save()} onCancel={() => setDraft(null)} />}

      {permissionGroups.map((group) => (
        <div key={group.id} style={{ borderBottom: '1px solid var(--border-soft)' }}>
          <div className="trow"
            style={{ gridTemplateColumns: 'minmax(0,1fr) 46px 46px 96px', gap: 6, padding: '8px 2px', border: 'none' }}>
            <span style={{ minWidth: 0, display: 'flex', alignItems: 'center', gap: 6 }}>
              <button className="btn btn-ghost btn-sm" style={{ padding: '0 4px' }}
                title="권한 펼치기"
                onClick={() => setExpanded(expanded === group.id ? null : group.id)}>
                {expanded === group.id ? '▾' : '▸'}
              </button>
              <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {group.name}
              </span>
              {group.systemFixed && (
                <span className="badge" style={{ fontSize: 10, padding: '1px 7px', color: 'var(--muted)', background: 'var(--chip)' }}>
                  고정
                </span>
              )}
            </span>
            <span className="muted2" style={{ fontSize: 11.5, textAlign: 'right' }}>
              {VISIBILITY_SCOPE_LABEL[group.visibilityScope]}
            </span>
            <span className="muted2" style={{ fontSize: 11.5, textAlign: 'right' }}>
              {group.memberCount}명
            </span>
            {pending === group.id ? (
              <span style={{ display: 'flex', gap: 4 }}>
                <button className="btn btn-danger btn-sm" disabled={busy}
                  onClick={() => void remove(group)}>확인</button>
                <button className="btn btn-ghost btn-sm"
                  onClick={() => setPending(null)}>취소</button>
              </span>
            ) : (
              <span style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                <button className="btn btn-ghost btn-sm" disabled={group.systemFixed}
                  title={group.systemFixed
                    ? '시스템 고정 그룹입니다 — 마지막 관리자가 스스로를 잠글 수 없게 막혀 있습니다'
                    : '이름·가시성·기능 권한 수정 (E5-2)'}
                  style={{ opacity: group.systemFixed ? .35 : 1 }}
                  onClick={() => { setDraft({ ...group }); setError(null) }}>
                  수정
                </button>
                <button className="btn btn-danger-ghost btn-sm"
                  disabled={group.systemFixed || group.memberCount > 0}
                  title={group.systemFixed
                    ? '시스템 고정 그룹은 삭제할 수 없습니다'
                    : group.memberCount > 0
                      ? `소속 인원 ${group.memberCount}명이 있어 삭제할 수 없습니다 — 먼저 인력 수정으로 그룹을 옮기세요`
                      : '삭제'}
                  style={{ opacity: group.systemFixed || group.memberCount > 0 ? .35 : 1 }}
                  onClick={() => { setPending(group.id); setError(null) }}>
                  삭제
                </button>
              </span>
            )}
          </div>

          {expanded === group.id && (
            <div style={{ padding: '2px 2px 10px 26px', display: 'grid', gap: 4 }}>
              {FLAGS.map((flag) => (
                <div key={flag.key} style={{ display: 'flex', gap: 8, fontSize: 12 }}>
                  <span style={{ width: 14, color: group[flag.key] ? 'var(--ok)' : 'var(--muted2)' }}>
                    {group[flag.key] ? '✓' : '·'}
                  </span>
                  <span className={group[flag.key] ? '' : 'muted2'}>{flag.label}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      ))}

      {permissionGroups.length === 0 && <Empty>권한 그룹이 없습니다.</Empty>}

      {error && (
        <div style={{ marginTop: 12 }}>
          <ErrorText code={error.code} message={error.message} />
        </div>
      )}

      <div className="muted2" style={{ fontSize: 11.5, marginTop: 12 }}>
        판정은 가시성 scope와 기능 플래그의 <strong>합집합</strong>입니다(상위 PRD §4-1).
        관리자 그룹은 고정이라 수정·삭제할 수 없습니다 — 마지막 관리자의 자기 잠금을 막습니다.
      </div>
    </section>
  )
}

/** 등록·수정 공용 폼 — 서버 PUT이 전체 치환이라 두 경우의 입력이 완전히 같다. */
function GroupForm({ draft, busy, onChange, onSave, onCancel }: {
  draft: Draft
  busy: boolean
  onChange: (draft: Draft) => void
  onSave: () => void
  onCancel: () => void
}) {
  return (
    <div style={{ display: 'grid', gap: 8, marginBottom: 12, background: 'var(--soft)', border: '1px solid var(--border-soft)', borderRadius: 10, padding: 10 }}>
      <div style={{ display: 'flex', gap: 6 }}>
        <input value={draft.name} placeholder="그룹명" autoFocus disabled={busy}
          onChange={(e) => onChange({ ...draft, name: e.target.value })}
          style={{ flex: 1, fontSize: 12.5, padding: '6px 8px', borderRadius: 7 }} />
        <select value={draft.visibilityScope} disabled={busy}
          onChange={(e) =>
            onChange({ ...draft, visibilityScope: e.target.value as VisibilityScope })}
          style={{ fontSize: 12.5, padding: '6px 8px', borderRadius: 7 }}>
          {VISIBILITY_SCOPE_ORDER.map((scope) => (
            <option key={scope} value={scope}>{VISIBILITY_SCOPE_LABEL[scope]}</option>
          ))}
        </select>
      </div>

      {FLAGS.map((flag) => (
        <label key={flag.key} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}>
          <input type="checkbox" checked={Boolean(draft[flag.key])} disabled={busy}
            onChange={(e) => onChange({ ...draft, [flag.key]: e.target.checked })} />
          {flag.label}
          <span className="muted2" style={{ fontSize: 11 }}>· {flag.hint}</span>
        </label>
      ))}

      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
        <button className="btn btn-ghost btn-sm" onClick={onCancel}>취소</button>
        <button className="btn btn-primary btn-sm" disabled={busy} onClick={onSave}>저장</button>
      </div>
    </div>
  )
}
