/*
 * 유지보수 이관 (AC D1-1) — 완료 프로젝트를 유지보수 계약으로 넘긴다.
 *
 * **필수값을 이관 시점에 받는 것이 이 폼의 요점이다**(D1-1): 계약사·계약명과 사이트
 * 1개 이상, 그리고 각 사이트의 담당 엔지니어다(기간·금액은 선택 — §4 표의 not null이
 * 그 셋뿐이다). 그래서 "유지보수중인데 계약 정보 없는 프로젝트"가 원천적으로 못
 * 생긴다 — 나중에 채우게 두면 그 상태가 생긴다.
 *
 * **담당 엔지니어가 사이트마다 필수인 이유**를 화면이 말해 준다: 비워 두면 그 사이트에
 * 올라온 이슈가 영원히 미배정으로 남는다(D3-1이 사이트에서 담당을 가져온다).
 *
 * **되돌릴 수 없다**는 것도 화면이 말한다: 이관 뒤에는 재개도 재이관도 막힌다(§5).
 * 확인 없이 누를 수 있는 버튼이 아니다.
 *
 * 서버가 400·409를 주면 프로젝트는 완료로 남고 계약도 만들어지지 않는다(D1-2·D1-3) —
 * 그래서 오류를 보여 주고 폼을 그대로 열어 둔다. 사용자가 고쳐서 다시 누르면 된다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { ErrorText, Field, Modal, ModalActions } from './ui'
import type { HandoverSiteBody, ProjectDetail } from '../types/api'

type SiteDraft = { name: string; engineerId: string }

export default function HandoverModal({ detail, onClose }: {
  detail: ProjectDetail
  onClose: () => void
}) {
  const { people, handover, showToast } = useStore()
  const [form, setForm] = useState({
    // 계약사는 프로젝트의 고객사가 그대로 이어지는 것이 보통이라 채워 둔다
    contractor: detail.client,
    name: `${detail.name} 유지보수`,
    startDate: '',
    endDate: '',
    amount: '',
    monthlyAmount: '',
  })
  const [sites, setSites] = useState<SiteDraft[]>([{ name: '', engineerId: '' }])
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const setSite = (index: number, patch: Partial<SiteDraft>) =>
    setSites((current) =>
      current.map((site, at) => (at === index ? { ...site, ...patch } : site)))

  // 사이트는 1개 이상이어야 하므로 마지막 하나는 지울 수 없다(D1-1)
  const removable = sites.length > 1
  const complete = form.contractor.trim() !== ''
    && form.name.trim() !== ''
    && sites.every((site) => site.name.trim() !== '' && site.engineerId !== '')

  const submit = async () => {
    setBusy(true)
    setError(null)
    const result = await handover({
      contractor: form.contractor.trim(),
      name: form.name.trim(),
      startDate: form.startDate === '' ? null : form.startDate,
      endDate: form.endDate === '' ? null : form.endDate,
      amount: form.amount === '' ? null : Number(form.amount),
      monthlyAmount: form.monthlyAmount === '' ? null : Number(form.monthlyAmount),
      sites: sites.map<HandoverSiteBody>((site) => ({
        name: site.name.trim(),
        engineerId: Number(site.engineerId),
      })),
      version: detail.version,
    })
    setBusy(false)

    if (!result.ok) {
      setError(result.error)

      return
    }

    showToast('유지보수로 이관했습니다')
    onClose()
  }

  return (
    <Modal title="유지보수로 이관" width={620} onClose={onClose}>
      <div className="muted" style={{ fontSize: 12.5, marginBottom: 14 }}>
        <b>{detail.name}</b>을(를) 유지보수 계약으로 넘깁니다.{' '}
        <span style={{ color: 'var(--danger, #c0392b)' }}>
          이관하면 되돌릴 수 없습니다
        </span>{' '}
        — 재개도 재이관도 막힙니다.
      </div>

      <Field label="계약사">
        <input value={form.contractor}
          onChange={(e) => setForm({ ...form, contractor: e.target.value })} />
      </Field>

      <Field label="계약명">
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
      </Field>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <Field label="시작일">
          <input type="date" value={form.startDate}
            onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
        </Field>
        <Field label="종료일">
          <input type="date" value={form.endDate}
            onChange={(e) => setForm({ ...form, endDate: e.target.value })} />
        </Field>
        <Field label="계약 금액">
          <input type="number" value={form.amount}
            onChange={(e) => setForm({ ...form, amount: e.target.value })} />
        </Field>
        <Field label="월 금액">
          <input type="number" value={form.monthlyAmount}
            onChange={(e) => setForm({ ...form, monthlyAmount: e.target.value })} />
        </Field>
      </div>

      <div style={{ marginTop: 6 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 6 }}>
          <label style={{ fontWeight: 600, fontSize: 12.5 }}>사이트</label>
          <span className="muted2" style={{ fontSize: 11.5 }}>
            1개 이상 · 담당 엔지니어는 필수입니다 (이 사람이 이슈 기본 담당자가 됩니다)
          </span>
          <button className="btn btn-ghost btn-sm" style={{ marginLeft: 'auto' }}
            onClick={() => setSites([...sites, { name: '', engineerId: '' }])}>
            + 사이트
          </button>
        </div>

        <div style={{ display: 'grid', gap: 6 }}>
          {sites.map((site, index) => (
            <div key={index}
              style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              <input value={site.name} placeholder="사이트명 (예: 명화공업 본사)"
                style={{ flex: 2 }}
                onChange={(e) => setSite(index, { name: e.target.value })} />
              <select value={site.engineerId} style={{ flex: 1.4 }}
                onChange={(e) => setSite(index, { engineerId: e.target.value })}>
                <option value="">— 담당 엔지니어 —</option>
                {people.map((person) => (
                  <option key={person.id} value={person.id}>{person.name}</option>
                ))}
              </select>
              <button className="btn btn-ghost btn-sm" disabled={!removable}
                title={removable ? '이 사이트를 뺍니다' : '사이트는 1개 이상이어야 합니다'}
                onClick={() => setSites(sites.filter((_, at) => at !== index))}>
                −
              </button>
            </div>
          ))}
        </div>
      </div>

      {error && <ErrorText code={error.code} message={error.message} />}

      <ModalActions>
        <button className="btn btn-ghost" onClick={onClose} disabled={busy}>취소</button>
        <button className="btn btn-primary" onClick={() => void submit()}
          disabled={busy || !complete}>
          {busy ? '이관 중…' : '이관'}
        </button>
      </ModalActions>
    </Modal>
  )
}
