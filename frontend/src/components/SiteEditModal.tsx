/*
 * 사이트 등록·수정 + 연락처 (AC D2-4) — "계약 관리" 플래그 보유자만.
 *
 * **연락처는 전체 교체다**(§7 PUT 의미론): 여기 남긴 것만 서버에 남는다. 화면이
 * 그것을 한 줄로 말해 준다 — "빼고 저장하면 지워진다"를 모르면 실수로 지운다.
 *
 * 연락처의 원문(`raw`)은 입력하지 않는다: 시트 적재분은 원문에서 조각을 뽑았지만
 * 수동 입력은 반대 방향이라, 서버가 조각을 시트와 같은 순서로 조립해 채운다
 * (2026-08-24 결정). 그래서 폼에는 조각만 있다.
 *
 * 담당 엔지니어를 비워 두는 것은 결함이 아니라 상태다 — 신규 예정·종료 사이트는
 * 미배정이고, 이슈 등록(D3-1)이 그 값을 기본 담당자로 쓴다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { CONTACT_PARTY_LABEL } from '../labels'
import { ErrorText, Field, Modal, ModalActions } from './ui'
import type { ContactBody, SiteView } from '../types/api'

export default function SiteEditModal({ contractId, site, onClose }: {
  contractId: number
  /** null이면 신규 등록 */
  site: SiteView | null
  onClose: () => void
}) {
  const { people, addSite, updateSite, showToast } = useStore()
  const [form, setForm] = useState({
    name: site?.name ?? '',
    channel: site?.channel ?? '',
    serverSpec: site?.serverSpec ?? '',
    engineerId: site?.engineer ? String(site.engineer.id) : '',
  })
  const [contacts, setContacts] = useState<ContactBody[]>(() =>
    (site?.contacts ?? []).map((contact) => ({
      party: contact.partyCode,
      name: contact.name,
      title: contact.title,
      phone: contact.phone,
      email: contact.email,
    })))
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const setContact = (index: number, patch: Partial<ContactBody>) =>
    setContacts((current) =>
      current.map((contact, at) => (at === index ? { ...contact, ...patch } : contact)))

  const submit = async () => {
    setBusy(true)
    setError(null)
    const body = {
      name: form.name.trim(),
      channel: form.channel === '' ? null : (form.channel as 'OEM' | 'ENT'),
      serverSpec: form.serverSpec.trim() === '' ? null : form.serverSpec.trim(),
      engineerId: form.engineerId === '' ? null : Number(form.engineerId),
      contacts,
    }
    const result = site === null
      ? await addSite(contractId, body)
      : await updateSite(site.id, { ...body, version: site.version })
    setBusy(false)

    if (!result.ok) {
      setError({ code: result.error.code, message: result.error.message })

      return
    }

    showToast(site === null ? '사이트를 등록했습니다' : '사이트를 수정했습니다')
    onClose()
  }

  return (
    <Modal title={site === null ? '사이트 등록' : `사이트 수정 — ${site.name}`}
      width={620} onClose={onClose}>
      <div style={{ display: 'grid', gap: 12 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1.4fr', gap: 12 }}>
          <Field label="사이트명" hint="고객사">
            <input value={form.name} autoFocus disabled={busy}
              onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </Field>
          <Field label="채널">
            <select value={form.channel} disabled={busy}
              onChange={(e) => setForm({ ...form, channel: e.target.value })}>
              <option value="">지정 안 함</option>
              <option value="OEM">OEM</option>
              <option value="ENT">ENT</option>
            </select>
          </Field>
          <Field label="담당 엔지니어" hint="이슈 기본 담당자가 된다">
            <select value={form.engineerId} disabled={busy}
              onChange={(e) => setForm({ ...form, engineerId: e.target.value })}>
              <option value="">미배정</option>
              {people.map((person) => (
                <option key={person.id} value={person.id}>{person.name}</option>
              ))}
            </select>
          </Field>
        </div>

        <Field label="서버 사양">
          <input value={form.serverSpec} disabled={busy}
            onChange={(e) => setForm({ ...form, serverSpec: e.target.value })} />
        </Field>

        <div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
            <strong style={{ fontSize: 12.5 }}>연락처 {contacts.length > 0 && `(${contacts.length})`}</strong>
            <button className="btn btn-ghost btn-sm" disabled={busy}
              onClick={() => setContacts([...contacts, {
                party: 'CLIENT', name: '', title: '', phone: '', email: '',
              }])}>
              + 연락처 추가
            </button>
          </div>

          <div style={{ display: 'grid', gap: 6 }}>
            {contacts.map((contact, index) => (
              <div key={index}
                style={{ display: 'grid', gridTemplateColumns: '84px 1fr 74px 1.1fr 1.3fr 44px', gap: 6, alignItems: 'center' }}>
                <select value={contact.party} disabled={busy}
                  onChange={(e) =>
                    setContact(index, { party: e.target.value as ContactBody['party'] })}
                  style={{ fontSize: 12, padding: '5px 6px', borderRadius: 6 }}>
                  {(['CONTRACTOR', 'CLIENT'] as const).map((party) => (
                    <option key={party} value={party}>{CONTACT_PARTY_LABEL[party]}</option>
                  ))}
                </select>
                <input placeholder="이름" value={contact.name ?? ''} disabled={busy}
                  onChange={(e) => setContact(index, { name: e.target.value })}
                  style={{ fontSize: 12, padding: '5px 7px', borderRadius: 6 }} />
                <input placeholder="직급" value={contact.title ?? ''} disabled={busy}
                  onChange={(e) => setContact(index, { title: e.target.value })}
                  style={{ fontSize: 12, padding: '5px 7px', borderRadius: 6 }} />
                <input placeholder="전화" value={contact.phone ?? ''} disabled={busy}
                  onChange={(e) => setContact(index, { phone: e.target.value })}
                  style={{ fontSize: 12, padding: '5px 7px', borderRadius: 6 }} />
                <input placeholder="이메일" value={contact.email ?? ''} disabled={busy}
                  onChange={(e) => setContact(index, { email: e.target.value })}
                  style={{ fontSize: 12, padding: '5px 7px', borderRadius: 6 }} />
                <button className="btn btn-danger-ghost btn-sm" disabled={busy}
                  title="이 연락처를 뺀다 — 저장하면 서버에서도 지워진다"
                  onClick={() => setContacts(contacts.filter((_, at) => at !== index))}>
                  ✕
                </button>
              </div>
            ))}
          </div>

          <div className="muted2" style={{ fontSize: 11.5, marginTop: 8 }}>
            저장하면 이 목록이 <strong>그대로</strong> 서버의 연락처가 됩니다 — 여기서 뺀
            연락처는 지워집니다. 이름·직급·전화·이메일 중 하나 이상은 있어야 합니다.
          </div>
        </div>
      </div>

      {error && <ErrorText code={error.code} message={error.message} />}

      <ModalActions>
        <button className="btn btn-ghost" onClick={onClose}>취소</button>
        <button className="btn btn-primary" disabled={busy} onClick={() => void submit()}>
          {busy ? '저장 중…' : site === null ? '등록' : '저장'}
        </button>
      </ModalActions>
    </Modal>
  )
}
