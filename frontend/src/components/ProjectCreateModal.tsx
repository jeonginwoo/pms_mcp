/*
 * 프로젝트 생성 (AC A1-1~A1-6) — 상태·진척률은 규칙으로 정해지므로 입력에 없다
 * (항상 계약대기·0에서 시작한다).
 *
 * PM 1명은 필수다(A1-4) — 화면에서도 선택을 강제하고, 서버가 422 PM_REQUIRED /
 * MULTIPLE_PM으로 다시 판정한다. 생성 권한은 권한 그룹 플래그가 판정하므로
 * (A1-5) 버튼을 미리 감추지 못한다 — 403이 오면 그 문구를 그대로 보여 준다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { ENGAGEMENT_LABEL, ROLE_LABEL } from '../labels'
import { invalidPeriodMessage, minEndDate } from '../periods'
import { ErrorText, Field, Modal } from './ui'
import type { AssignmentSpecBody, Engagement, ProjectRole } from '../types/api'

const ENGAGEMENTS: Engagement[] = ['REMOTE', 'ONSITE', 'PARTIAL_ONSITE']
const MEMBER_ROLES: ProjectRole[] = ['PL', 'PARTICIPANT']

interface MemberRow {
  personId: string
  role: ProjectRole
  monthlyMm: string
}

export default function ProjectCreateModal({ onClose }: { onClose: () => void }) {
  const { people, createProject, openProject, showToast } = useStore()
  const [form, setForm] = useState({
    client: '',
    name: '',
    solution: '',
    engagement: 'REMOTE' as Engagement,
    contractMm: '1',
    startDate: '',
    endDate: '',
    managerId: '',
    managerMm: '0.5',
  })
  const [members, setMembers] = useState<MemberRow[]>([])
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const set = (patch: Partial<typeof form>) => setForm((current) => ({ ...current, ...patch }))

  const submit = async () => {
    if (!form.managerId) {
      setError({ code: 'PM_REQUIRED', message: 'PM을 1명 지정해야 합니다' })

      return
    }

    const periodError = invalidPeriodMessage(form.startDate, form.endDate)

    if (periodError) {
      setError({ code: 'VALIDATION_ERROR', message: periodError })

      return
    }

    const assignments: AssignmentSpecBody[] = [
      { personId: Number(form.managerId), role: 'PM', monthlyMm: Number(form.managerMm) || 0 },
      ...members
        .filter((member) => member.personId !== '')
        .map((member) => ({
          personId: Number(member.personId),
          role: member.role,
          monthlyMm: Number(member.monthlyMm) || 0,
        })),
    ]

    setBusy(true)
    setError(null)
    const result = await createProject({
      client: form.client.trim(),
      name: form.name.trim(),
      solution: form.solution.trim() || null,
      engagement: form.engagement,
      contractMm: Number(form.contractMm) || 0,
      startDate: form.startDate || null,
      endDate: form.endDate || null,
      assignments,
    })
    setBusy(false)

    if (result.ok) {
      showToast('프로젝트가 생성되었습니다 (계약대기)')
      onClose()
      await openProject(result.value.id)

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  return (
    <Modal title="새 프로젝트" width={560} onClose={onClose}>
      <div style={{ display: 'grid', gap: 12 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label="고객사">
            <input value={form.client} autoFocus
              onChange={(e) => set({ client: e.target.value })} />
          </Field>
          <Field label="프로젝트명">
            <input value={form.name} onChange={(e) => set({ name: e.target.value })} />
          </Field>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
          <Field label="솔루션">
            <input value={form.solution} onChange={(e) => set({ solution: e.target.value })} />
          </Field>
          <Field label="수행 형태">
            <select value={form.engagement}
              onChange={(e) => set({ engagement: e.target.value as Engagement })}>
              {ENGAGEMENTS.map((value) => (
                <option key={value} value={value}>{ENGAGEMENT_LABEL[value]}</option>
              ))}
            </select>
          </Field>
          <Field label="계약 M/M">
            <input type="number" step="0.1" min="0" value={form.contractMm}
              onChange={(e) => set({ contractMm: e.target.value })} />
          </Field>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label="시작일">
            <input type="date" value={form.startDate}
              onChange={(e) => set({ startDate: e.target.value })} />
          </Field>
          <Field label="종료일" hint="시작일보다 뒤">
            <input type="date" value={form.endDate} min={minEndDate(form.startDate)}
              onChange={(e) => set({ endDate: e.target.value })} />
          </Field>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 90px', gap: 12 }}>
          <Field label="PM (필수)">
            <select value={form.managerId} onChange={(e) => set({ managerId: e.target.value })}>
              <option value="">인원 선택…</option>
              {people.map((person) => (
                <option key={person.id} value={person.id}>
                  {person.name} · {person.orgUnit} {person.grade}
                </option>
              ))}
            </select>
          </Field>
          <Field label="M/M">
            <input type="number" step="0.05" min="0" value={form.managerMm}
              onChange={(e) => set({ managerMm: e.target.value })} />
          </Field>
        </div>

        {members.map((member, index) => (
          <div key={index} style={{ display: 'grid', gridTemplateColumns: '1fr 110px 90px 28px', gap: 8, alignItems: 'end' }}>
            <Field label={`참여자 ${index + 1}`}>
              <select value={member.personId}
                onChange={(e) => setMembers((rows) => rows.map((row, i) =>
                  (i === index ? { ...row, personId: e.target.value } : row)))}>
                <option value="">인원 선택…</option>
                {people.map((person) => (
                  <option key={person.id} value={person.id}>
                    {person.name} · {person.orgUnit}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="역할">
              <select value={member.role}
                onChange={(e) => setMembers((rows) => rows.map((row, i) =>
                  (i === index ? { ...row, role: e.target.value as ProjectRole } : row)))}>
                {MEMBER_ROLES.map((value) => (
                  <option key={value} value={value}>{ROLE_LABEL[value]}</option>
                ))}
              </select>
            </Field>
            <Field label="M/M">
              <input type="number" step="0.05" min="0" value={member.monthlyMm}
                onChange={(e) => setMembers((rows) => rows.map((row, i) =>
                  (i === index ? { ...row, monthlyMm: e.target.value } : row)))} />
            </Field>
            <button className="btn btn-ghost btn-sm" style={{ height: 34 }}
              onClick={() => setMembers((rows) => rows.filter((_, i) => i !== index))}>×</button>
          </div>
        ))}

        <button className="btn btn-ghost btn-sm" style={{ justifySelf: 'start' }}
          onClick={() => setMembers((rows) =>
            [...rows, { personId: '', role: 'PARTICIPANT', monthlyMm: '0.5' }])}>
          + 참여자 추가
        </button>

        <div className="muted2" style={{ fontSize: 11.5 }}>
          기간을 비운 배정은 프로젝트 기간으로 채워집니다 · 생성 시 상태는 항상 계약대기입니다
        </div>

        {error && <ErrorText code={error.code} message={error.message} />}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4 }}>
          <button className="btn btn-ghost" onClick={onClose}>취소</button>
          <button className="btn btn-primary" disabled={busy} onClick={() => void submit()}>
            {busy ? '생성 중…' : '생성'}
          </button>
        </div>
      </div>
    </Modal>
  )
}
