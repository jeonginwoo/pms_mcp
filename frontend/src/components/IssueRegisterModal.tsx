/*
 * 이슈 등록 (AC D3-1) — **로그인 사용자 전체**가 쓸 수 있다.
 *
 * 계약 쓰기(D2)와 달리 "계약 관리" 플래그를 보지 않는다: 이슈는 현장에서 올라오는
 * 것이고 US-D3의 대괄호가 그 차이를 적어 뒀다. 그래서 이 모달을 여는 버튼도
 * 권한으로 감추지 않는다.
 *
 * **담당자 칸이 없는 것은 누락이 아니다** — 서버가 사이트의 담당 엔지니어를
 * 기본 담당자로 넣는다(D3-1). 그래서 사이트를 고르는 자리에 담당자를 함께 보여
 * 준다: 등록하면 누구에게 알림이 가는지 미리 알 수 있어야 한다.
 *
 * 사이트는 계약 아래에 있으므로 선택이 두 단계다(계약 → 사이트). 이슈 목록에서
 * 열면 계약부터, 계약 상세에서 열면 그 계약이 정해진 채로 시작한다.
 */
import { useEffect, useState } from 'react'
import { useStore } from '../store'
import { ISSUE_TYPE_LABEL } from '../labels'
import { ErrorText, Field, Modal, ModalActions } from './ui'
import type { ContractSummary, IssueType, SiteView } from '../types/api'

const TYPES: IssueType[] = ['INCIDENT', 'INQUIRY', 'REQUEST']

export default function IssueRegisterModal({ contractId, onClose, onRegistered }: {
  /** 계약이 정해진 채로 열 수 있다 — 계약 상세의 사이트 행에서 열 때 */
  contractId?: number
  onClose: () => void
  onRegistered: () => void
}) {
  const { loadContracts, loadContractDetail, registerIssue, showToast } = useStore()
  const [contracts, setContracts] = useState<ContractSummary[]>([])
  const [keyword, setKeyword] = useState('')
  const [pickedContract, setPickedContract] = useState<number | null>(contractId ?? null)
  const [sites, setSites] = useState<SiteView[]>([])
  const [form, setForm] = useState<{ siteId: string; type: IssueType; title: string; content: string }>({
    siteId: '',
    type: 'INCIDENT',
    title: '',
    content: '',
  })
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  // 계약 목록은 계약이 주어지지 않았을 때만 필요하다
  useEffect(() => {
    if (contractId !== undefined) {
      return
    }

    let live = true
    void loadContracts({ keyword: keyword.trim() === '' ? null : keyword.trim() })
      .then((result) => {
        if (live && result.ok) {
          setContracts(result.value.content)
        }
      })

    return () => { live = false }
  }, [contractId, keyword, loadContracts])

  // 고른 계약의 사이트를 읽는다 — 이슈는 사이트에 붙는다
  useEffect(() => {
    if (pickedContract === null) {
      setSites([])

      return
    }

    let live = true
    void loadContractDetail(pickedContract).then((result) => {
      if (live && result.ok) {
        setSites(result.value.sites)
        // 사이트가 하나뿐이면 고를 것이 없다
        setForm((current) => ({
          ...current,
          siteId: result.value.sites.length === 1 ? String(result.value.sites[0].id) : '',
        }))
      }
    })

    return () => { live = false }
  }, [pickedContract, loadContractDetail])

  const picked = sites.find((site) => String(site.id) === form.siteId) ?? null

  const submit = async () => {
    setBusy(true)
    setError(null)
    const result = await registerIssue({
      siteId: Number(form.siteId),
      type: form.type,
      title: form.title.trim(),
      // 본문은 선택이다 — 안 적으면 제목만 있는 이슈가 된다(시드 이슈와 같은 모습)
      content: form.content.trim() === '' ? null : form.content,
    })
    setBusy(false)

    if (!result.ok) {
      setError(result.error)

      return
    }

    showToast(picked?.engineer
      ? `이슈를 등록했습니다 — 담당 ${picked.engineer.name}`
      : '이슈를 등록했습니다 (미배정)')
    onRegistered()
    onClose()
  }

  return (
    <Modal title="이슈 등록" width={560} onClose={onClose}>
      {contractId === undefined && (
        <Field label="계약" hint="계약명·계약사·고객사로 찾습니다">
          <input value={keyword} onChange={(e) => setKeyword(e.target.value)}
            placeholder="예: 가천대길병원" style={{ marginBottom: 6 }} />
          <select value={pickedContract === null ? '' : String(pickedContract)}
            onChange={(e) =>
              setPickedContract(e.target.value === '' ? null : Number(e.target.value))}>
            <option value="">— 계약 선택 —</option>
            {contracts.map((contract) => (
              <option key={contract.id} value={contract.id}>
                {contract.contractor} · {contract.name}
              </option>
            ))}
          </select>
        </Field>
      )}

      <Field label="사이트" hint="이 사이트의 담당 엔지니어가 기본 담당자가 됩니다">
        <select value={form.siteId} disabled={pickedContract === null}
          onChange={(e) => setForm({ ...form, siteId: e.target.value })}>
          <option value="">— 사이트 선택 —</option>
          {sites.map((site) => (
            <option key={site.id} value={site.id}>
              {site.name}{site.engineer ? ` (담당 ${site.engineer.name})` : ' (미배정)'}
            </option>
          ))}
        </select>
        {pickedContract !== null && sites.length === 0 && (
          <div className="muted2" style={{ fontSize: 11.5, marginTop: 4 }}>
            이 계약에는 등록된 사이트가 없습니다 — 사이트를 먼저 등록해야 합니다.
          </div>
        )}
      </Field>

      <Field label="유형">
        <select value={form.type}
          onChange={(e) => setForm({ ...form, type: e.target.value as IssueType })}>
          {TYPES.map((type) => (
            <option key={type} value={type}>{ISSUE_TYPE_LABEL[type]}</option>
          ))}
        </select>
      </Field>

      <Field label="제목">
        <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })}
          placeholder="예: 로그인 지연 현상" />
      </Field>

      <Field label="본문" hint="선택입니다 — 나중에 수정할 수 있습니다">
        <textarea value={form.content} rows={4} style={{ width: '100%' }}
          onChange={(e) => setForm({ ...form, content: e.target.value })}
          placeholder="증상·재현 절차·요청 내용" />
      </Field>

      <div className="muted2" style={{ fontSize: 11.5, marginTop: 4 }}>
        상태는 <b>접수</b>로, 접수일은 오늘로 기록됩니다.
        {picked?.engineer && ` 등록하면 ${picked.engineer.name}님에게 알림이 갑니다.`}
      </div>

      {error && <ErrorText code={error.code} message={error.message} />}

      <ModalActions>
        <button className="btn btn-ghost" onClick={onClose} disabled={busy}>취소</button>
        <button className="btn btn-primary" onClick={() => void submit()}
          disabled={busy || form.siteId === '' || form.title.trim() === ''}>
          {busy ? '등록 중…' : '등록'}
        </button>
      </ModalActions>
    </Modal>
  )
}
