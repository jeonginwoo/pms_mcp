/*
 * 배정 패널 (EPIC B + A6-1·A6-3) — 추가·기간/M/M 수정·종료·역할 변경.
 *
 * 배정 M/M은 **실투입 계획**이다(상위 PRD §3 · B1-5) — 계약 M/M을 나눠 담는 숫자가
 * 아니라는 것을 레이블에 명시한다. 쓰기 UI는 PM에게만 보이고(§4-2 ASSIGN), 그 외에는
 * 읽기 표로만 남는다. 새 배정을 만들 때 role=PM은 서버가 422로 막으므로(A6-5 불변식은
 * PM 교체 경로만 지킬 수 있다) **추가 폼의** 역할 선택에는 PM이 없다 — 이미 배정된
 * 인원의 역할 드롭다운에는 있고, 그것을 고르면 PM 교체 경로로 간다(`RoleChangeModal`).
 */
import { useState } from 'react'
import { useStore } from '../store'
import { ROLE_LABEL, period } from '../labels'
import { ErrorText, RoleBadge } from './ui'
import RoleChangeModal from './RoleChangeModal'
import type { AssignmentView, ProjectRole } from '../types/api'

const ASSIGNABLE_ROLES: ProjectRole[] = ['PL', 'PARTICIPANT']

/** 역할 드롭다운의 값 3종 — PM을 고르면 화면이 PM 교체 경로로 보낸다 (A6-7). */
const SELECTABLE_ROLES: ProjectRole[] = ['PARTICIPANT', 'PL', 'PM']

interface Props {
  /** PM만 쓰기 UI를 본다 — 판정은 서버가 다시 한다 */
  editable: boolean
}

export default function AssignmentPanel({ editable }: Props) {
  const {
    detail, people, assign, updateAssignment, closeAssignment, showToast,
  } = useStore()
  const [adding, setAdding] = useState(false)
  const [personId, setPersonId] = useState('')
  const [role, setRole] = useState<ProjectRole>('PARTICIPANT')
  const [monthlyMm, setMonthlyMm] = useState('0.5')
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [roleDraft, setRoleDraft] = useState<Record<number, ProjectRole>>({})
  const [confirming, setConfirming] =
    useState<{ assignment: AssignmentView; nextRole: ProjectRole } | null>(null)

  if (!detail) {
    return null
  }

  const assigned = new Set(detail.assignments.map((assignment) => assignment.personId))
  const candidates = people.filter((person) => !assigned.has(person.id))
  const totalMm = Math.round(
    detail.assignments.reduce((sum, assignment) => sum + assignment.monthlyMm, 0) * 100) / 100

  const report = (result: { ok: boolean; error?: { code: string; message: string } },
      message: string) => {
    if (result.ok) {
      setError(null)
      showToast(message)

      return true
    }

    setError(result.error ?? null)

    return false
  }

  const submitAdd = async () => {
    if (!personId) {
      setError({ code: 'VALIDATION_ERROR', message: '배정할 인원을 선택해 주세요' })

      return
    }

    setBusy(true)
    const result = await assign({
      personId: Number(personId),
      role,
      monthlyMm: Number(monthlyMm) || 0,
    })
    setBusy(false)

    if (report(result, '배정되었습니다')) {
      setAdding(false)
      setPersonId('')
      setMonthlyMm('0.5')
      setRole('PARTICIPANT')
    }
  }

  const submitMm = async (assignmentId: number, next: number, current: number,
      startDate: string | null, endDate: string | null, version: number) => {
    if (next === current) {
      return
    }

    setBusy(true)
    const result = await updateAssignment(assignmentId,
      { startDate, endDate, monthlyMm: next, version })
    setBusy(false)
    report(result, 'M/M이 수정되었습니다')
  }

  const submitClose = async (assignmentId: number) => {
    setBusy(true)
    const result = await closeAssignment(assignmentId)
    setBusy(false)
    report(result, '배정이 종료되었습니다')
  }


  return (
    <section className="card" style={{ padding: '18px 20px' }}>
      <h3 style={{ marginBottom: 4 }}>
        배정{' '}
        <span className="muted2" style={{ fontWeight: 500, fontSize: 12 }}>
          {detail.assignments.length}명 · {totalMm} M/M
        </span>
      </h3>
      <div className="muted2" style={{ fontSize: 11, marginBottom: 10 }}>
        M/M은 실투입 계획입니다 (계약 M/M 배분이 아닙니다)
      </div>

      {detail.assignments.map((assignment) => (
        <div key={assignment.id}
          style={{ borderBottom: '1px solid var(--border-soft)', padding: '8px 2px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 58px 66px 22px', gap: 8, alignItems: 'center' }}>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {assignment.personName ?? `#${assignment.personId}`}
                {!assignment.personActive && (
                  <span className="muted2"
                    style={{ fontWeight: 500, fontSize: 11, marginLeft: 4 }}>
                    (퇴사)
                  </span>
                )}
              </div>
              <div className="muted2" style={{ fontSize: 11 }}>
                {period(assignment.startDate, assignment.endDate)}
              </div>
            </div>
            <RoleBadge role={assignment.role} />
            {editable ? (
              <input key={`${assignment.id}-${assignment.monthlyMm}-${assignment.version}`}
                type="number" step="0.05" min="0" defaultValue={assignment.monthlyMm}
                disabled={busy}
                onBlur={(e) => void submitMm(assignment.id, Number(e.target.value) || 0,
                  assignment.monthlyMm, assignment.startDate, assignment.endDate,
                  assignment.version)}
                onKeyDown={(e) => { if (e.key === 'Enter') { e.currentTarget.blur() } }}
                style={{ fontSize: 12.5, fontWeight: 600, padding: '4px 6px', borderRadius: 7, textAlign: 'right', width: '100%' }} />
            ) : (
              <span style={{ textAlign: 'right', fontWeight: 600, fontSize: 12.5 }}>
                {assignment.monthlyMm}
              </span>
            )}
            {editable && assignment.role !== 'PM' && (
              <button title="배정 종료" disabled={busy}
                onClick={() => void submitClose(assignment.id)}
                style={{ background: 'none', border: 'none', color: 'var(--muted2)', fontSize: 15, lineHeight: 1, padding: 2 }}>
                ×
              </button>
            )}
          </div>
          {/*
            역할 변경은 **드롭다운 + [수정] + 확인**이다 (2026-08-24 사용자 결정).
            구 화면에는 "이 사람을 PM으로 지정" 버튼 하나뿐이어서 참여자↔PL을 바꿀 수
            없었고(서버에도 `/roles`가 없었다), 한 번의 클릭으로 PM이 바뀌었다.
            PM 행에는 드롭다운을 두지 않는다: PM을 다른 역할로 내리는 요청은 서버가
            422로 막는다(PM 없는 프로젝트가 생긴다 — A6-5). PM 교체는 새 PM이 될
            사람의 행에서 한다.
          */}
          {editable && assignment.role !== 'PM' && (
            <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginTop: 4 }}>
              <select value={roleDraft[assignment.id] ?? assignment.role} disabled={busy}
                onChange={(e) => setRoleDraft((current) => ({
                  ...current, [assignment.id]: e.target.value as ProjectRole,
                }))}
                style={{ fontSize: 12, padding: '3px 6px', borderRadius: 6 }}>
                {SELECTABLE_ROLES.map((value) => (
                  <option key={value} value={value}>{ROLE_LABEL[value]}</option>
                ))}
              </select>
              <button className="btn btn-ghost btn-sm"
                disabled={busy
                  || (roleDraft[assignment.id] ?? assignment.role) === assignment.role}
                title="확인 후 바뀝니다"
                onClick={() => setConfirming({
                  assignment,
                  nextRole: roleDraft[assignment.id] ?? assignment.role,
                })}>
                수정
              </button>
            </div>
          )}
          {/* 퇴사한 PM의 배정은 종료하지 않는다 — 종료하면 PM 공석이 되고
              "프로젝트당 PM 1행" 불변식(A6-5)이 깨진다. 교체로 유도한다 */}
          {editable && assignment.role === 'PM' && !assignment.personActive && (
            <div className="muted2" style={{ fontSize: 11.5, marginTop: 2 }}>
              퇴사한 PM입니다 — 배정 종료가 아니라 PM 교체가 필요합니다
            </div>
          )}
        </div>
      ))}

      {editable && (adding ? (
        <div style={{ display: 'grid', gap: 7, marginTop: 12, background: 'var(--soft)', border: '1px solid var(--border-soft)', borderRadius: 10, padding: 10 }}>
          <select value={personId} onChange={(e) => setPersonId(e.target.value)}
            style={{ fontSize: 12.5, padding: '6px 8px', borderRadius: 7, width: '100%' }}>
            <option value="">인원 선택…</option>
            {candidates.map((person) => (
              <option key={person.id} value={person.id}>
                {person.name} · {person.orgUnit} {person.grade}
              </option>
            ))}
          </select>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
            <select value={role} onChange={(e) => setRole(e.target.value as ProjectRole)}
              style={{ fontSize: 12.5, padding: '6px 8px', borderRadius: 7 }}>
              {ASSIGNABLE_ROLES.map((value) => (
                <option key={value} value={value}>{ROLE_LABEL[value]}</option>
              ))}
            </select>
            <input type="number" step="0.05" min="0" value={monthlyMm}
              onChange={(e) => setMonthlyMm(e.target.value)}
              style={{ fontSize: 12.5, fontWeight: 600, padding: '6px 8px', borderRadius: 7, width: 70, textAlign: 'right' }} />
            <span className="muted" style={{ fontSize: 11.5, fontWeight: 600 }}>M/M</span>
            <span style={{ flex: 1 }} />
            <button className="btn btn-primary btn-sm" disabled={busy}
              onClick={() => void submitAdd()}>배정</button>
            <button className="btn btn-ghost btn-sm" onClick={() => { setAdding(false); setError(null) }}>
              취소
            </button>
          </div>
          <div className="muted2" style={{ fontSize: 11 }}>
            기간을 비우면 프로젝트 기간으로 채워집니다 · 역할 변경은 목록의 드롭다운으로
          </div>
        </div>
      ) : (
        <button onClick={() => setAdding(true)}
          style={{ marginTop: 12, width: '100%', background: 'var(--surface)', border: '1px dashed var(--border)', color: 'var(--primary)', borderRadius: 9, padding: 8, fontSize: 12.5, fontWeight: 600 }}>
          + 인원 배정
        </button>
      ))}

      {!editable && (
        <div className="muted2" style={{ fontSize: 11.5, marginTop: 10 }}>
          배정·M/M 입력과 역할 변경은 PM만 가능합니다 (상위 PRD §4-2).
        </div>
      )}

      {error && (
        <div style={{ marginTop: 10 }}>
          <ErrorText code={error.code} message={error.message} />
        </div>
      )}

      {confirming && (
        <RoleChangeModal assignment={confirming.assignment} nextRole={confirming.nextRole}
          onClose={() => {
            // 초안을 지운다 — 취소했든 바꿨든 다음 표시는 서버가 준 역할이어야 한다
            setRoleDraft((current) => {
              const next = { ...current }
              delete next[confirming.assignment.id]

              return next
            })
            setConfirming(null)
          }} />
      )}
    </section>
  )
}
