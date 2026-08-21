/*
 * 정보 수정 (AC A5-1~A5-3) — 전체 치환(PUT)이되 **상태는 건드리지 않는다**.
 *
 * status 선택을 뺀 이유(2026-08-22 사용자 결정): 상태 전이는 조회 화면의 전용 버튼
 * (`StatusAdvance`)이 확인 카드와 함께 담당한다. 폼에도 두면 같은 규칙의 입구가 둘이
 * 되고, 되돌릴 수 없는 변경이 "정보 저장"에 섞여 들어간다.
 * 서버 계약은 전체 치환이므로 현재 status를 그대로 실어 보낸다(`editBodyOf`).
 * PM 교체도 이 경로에 없다 — 전용 경로(US-A6)의 몫이다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { ENGAGEMENT_LABEL, STATUS_LABEL } from '../labels'
import { invalidPeriodMessage, minEndDate } from '../periods'
import { editBodyOf } from '../projectBody'
import { ErrorText, Field, Modal } from './ui'
import type { Engagement } from '../types/api'

const ENGAGEMENTS: Engagement[] = ['REMOTE', 'ONSITE', 'PARTIAL_ONSITE']

export default function ProjectEditModal({ onClose }: { onClose: () => void }) {
  const { detail, editProject, showToast } = useStore()
  const [form, setForm] = useState({
    client: detail?.client ?? '',
    name: detail?.name ?? '',
    solution: detail?.solution ?? '',
    engagement: detail?.engagement ?? ('REMOTE' as Engagement),
    contractMm: String(detail?.contractMm ?? 0),
    startDate: detail?.startDate ?? '',
    endDate: detail?.endDate ?? '',
  })
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  if (!detail) {
    return null
  }

  const set = (patch: Partial<typeof form>) => setForm((current) => ({ ...current, ...patch }))

  const submit = async () => {
    const periodError = invalidPeriodMessage(form.startDate, form.endDate)

    if (periodError) {
      setError({ code: 'VALIDATION_ERROR', message: periodError })

      return
    }

    setBusy(true)
    setError(null)
    const result = await editProject(editBodyOf(detail, {
      client: form.client.trim(),
      name: form.name.trim(),
      solution: form.solution.trim() || null,
      engagement: form.engagement,
      contractMm: Number(form.contractMm) || 0,
      startDate: form.startDate || null,
      endDate: form.endDate || null,
    }))
    setBusy(false)

    if (result.ok) {
      showToast('수정되었습니다')
      onClose()

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  return (
    <Modal title="프로젝트 정보 수정" onClose={onClose}>
      <div style={{ display: 'grid', gap: 12 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label="고객사">
            <input value={form.client} onChange={(e) => set({ client: e.target.value })} />
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

        <div className="muted2" style={{ fontSize: 11.5 }}>
          상태({STATUS_LABEL[detail.status]})는 이 폼에서 바뀌지 않습니다 — 상세 화면의 상태
          버튼으로 한 칸씩 옮깁니다. 저장은 v{detail.version} 기준입니다.
        </div>

        {error && <ErrorText code={error.code} message={error.message} />}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4 }}>
          <button className="btn btn-ghost" onClick={onClose}>취소</button>
          <button className="btn btn-primary" disabled={busy} onClick={() => void submit()}>
            {busy ? '저장 중…' : '저장'}
          </button>
        </div>
      </div>
    </Modal>
  )
}
