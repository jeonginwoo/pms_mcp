/*
 * 유지보수 계약 등록·수정 (AC D2-1·D2-2) — "계약 관리" 플래그 보유자만.
 *
 * 등록과 수정이 한 모달인 이유는 서버 본문이 같기 때문이다(차이는 `version` 하나).
 * 나누면 13개 필드의 폼이 두 벌이 되고 한쪽만 고쳐지는 날이 온다.
 *
 * **시트 유래 필드는 폼에 없다**(`sheetSection`·`contractDateNote`): 원본 보존이
 * 목적인 칸이라 화면이 덮어쓸 수 있게 두면 그 칸의 존재 이유가 사라진다.
 * 상태 선택은 `statusCode`(열거 이름)로 되채운다 — 표시용 `status`는 한국어 라벨이라
 * 그것으로 select를 맞추려면 라벨→이름 표를 클라이언트가 또 갖게 된다.
 *
 * 기간·금액에 규칙을 걸지 않는 것은 서버와 같은 이유다: 시드 105건 중 종료일이
 * 시작일보다 이른 계약이 실제로 1건 있다. AC에 없는 규칙을 화면이 지어내면 실
 * 데이터를 등록할 수 없게 된다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { CONTRACT_STATUS_LABEL, CONTRACT_STATUS_ORDER } from '../labels'
import { ErrorText, Field, Modal, ModalActions } from './ui'
import type { ContractDetail, ContractStatus } from '../types/api'

interface Form {
  contractor: string
  name: string
  status: ContractStatus
  contractDate: string
  startDate: string
  endDate: string
  amount: string
  monthlyAmount: string
  salesRepId: string
  category: string
  targetInfra: string
  regularCheck: string
  note: string
}

export default function ContractEditModal({ contract, onClose }: {
  /** null이면 신규 등록 (D2-1 — 원천 프로젝트 없는 직접 등록) */
  contract: ContractDetail | null
  onClose: () => void
}) {
  const { people, createContract, updateContract, openContract, showToast } = useStore()
  const [form, setForm] = useState<Form>(() => initialForm(contract))
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const set = (patch: Partial<Form>) => setForm((current) => ({ ...current, ...patch }))

  const submit = async () => {
    setBusy(true)
    setError(null)
    const body = {
      contractor: form.contractor.trim(),
      name: form.name.trim(),
      status: form.status,
      contractDate: blankToNull(form.contractDate),
      startDate: blankToNull(form.startDate),
      endDate: blankToNull(form.endDate),
      amount: numberOrNull(form.amount),
      monthlyAmount: numberOrNull(form.monthlyAmount),
      salesRepId: numberOrNull(form.salesRepId),
      category: blankToNull(form.category),
      targetInfra: blankToNull(form.targetInfra),
      regularCheck: blankToNull(form.regularCheck),
      note: blankToNull(form.note),
    }
    const result = contract === null
      ? await createContract(body)
      : await updateContract(contract.id, { ...body, version: contract.version })
    setBusy(false)

    if (!result.ok) {
      setError({ code: result.error.code, message: result.error.message })

      return
    }

    showToast(contract === null ? '계약을 등록했습니다' : '계약을 수정했습니다')

    // 새 계약은 바로 상세로 들어간다 — 다음 할 일이 사이트 등록이고 그 자리가 상세다
    if (contract === null) {
      await openContract(result.value.id)
    }

    onClose()
  }

  return (
    <Modal title={contract === null ? '계약 등록' : `계약 수정 — ${contract.name}`}
      width={600} onClose={onClose}>
      <div style={{ display: 'grid', gap: 12 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label="계약사">
            <input value={form.contractor} autoFocus disabled={busy}
              onChange={(e) => set({ contractor: e.target.value })} />
          </Field>
          <Field label="상태">
            <select value={form.status} disabled={busy}
              onChange={(e) => set({ status: e.target.value as ContractStatus })}>
              {CONTRACT_STATUS_ORDER.map((status) => (
                <option key={status} value={status}>{CONTRACT_STATUS_LABEL[status]}</option>
              ))}
            </select>
          </Field>
        </div>

        <Field label="계약명">
          <input value={form.name} disabled={busy}
            onChange={(e) => set({ name: e.target.value })} />
        </Field>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
          <Field label="계약일">
            <input type="date" value={form.contractDate} disabled={busy}
              onChange={(e) => set({ contractDate: e.target.value })} />
          </Field>
          <Field label="시작일">
            <input type="date" value={form.startDate} disabled={busy}
              onChange={(e) => set({ startDate: e.target.value })} />
          </Field>
          <Field label="종료일">
            <input type="date" value={form.endDate} disabled={busy}
              onChange={(e) => set({ endDate: e.target.value })} />
          </Field>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
          <Field label="계약 금액" hint="원">
            <input type="number" min="0" value={form.amount} disabled={busy}
              onChange={(e) => set({ amount: e.target.value })} />
          </Field>
          <Field label="월 금액" hint="원">
            <input type="number" min="0" value={form.monthlyAmount} disabled={busy}
              onChange={(e) => set({ monthlyAmount: e.target.value })} />
          </Field>
          <Field label="영업대표">
            <select value={form.salesRepId} disabled={busy}
              onChange={(e) => set({ salesRepId: e.target.value })}>
              <option value="">지정 안 함</option>
              {people.map((person) => (
                <option key={person.id} value={person.id}>{person.name}</option>
              ))}
            </select>
          </Field>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label="분류" hint="검색엔진 · 인프라">
            <input value={form.category} disabled={busy}
              onChange={(e) => set({ category: e.target.value })} />
          </Field>
          <Field label="대상" hint="라이선스·제품 사양">
            <input value={form.targetInfra} disabled={busy}
              onChange={(e) => set({ targetInfra: e.target.value })} />
          </Field>
        </div>

        <Field label="정기점검" hint="정보 텍스트 — 일정 엔진·자동 이슈는 없다">
          <input value={form.regularCheck} disabled={busy}
            onChange={(e) => set({ regularCheck: e.target.value })} />
        </Field>

        <Field label="비고">
          <textarea rows={2} value={form.note} disabled={busy}
            onChange={(e) => set({ note: e.target.value })} />
        </Field>
      </div>

      {error && <ErrorText code={error.code} message={error.message} />}

      <ModalActions>
        <button className="btn btn-ghost" onClick={onClose}>취소</button>
        <button className="btn btn-primary" disabled={busy} onClick={() => void submit()}>
          {busy ? '저장 중…' : contract === null ? '등록' : '저장'}
        </button>
      </ModalActions>
    </Modal>
  )
}

function initialForm(contract: ContractDetail | null): Form {
  return {
    contractor: contract?.contractor ?? '',
    name: contract?.name ?? '',
    // 라벨(status)이 아니라 열거 이름(statusCode)으로 되채운다
    status: contract?.statusCode ?? 'ACTIVE',
    contractDate: contract?.contractDate ?? '',
    startDate: contract?.startDate ?? '',
    endDate: contract?.endDate ?? '',
    amount: contract?.amount != null ? String(contract.amount) : '',
    monthlyAmount: contract?.monthlyAmount != null ? String(contract.monthlyAmount) : '',
    salesRepId: contract?.salesRep ? String(contract.salesRep.id) : '',
    category: contract?.category ?? '',
    targetInfra: contract?.targetInfra ?? '',
    regularCheck: contract?.regularCheck ?? '',
    note: contract?.note ?? '',
  }
}

/** 빈 입력은 "값 없음"이다 — 빈 문자열을 보내면 서버가 그것을 값으로 저장한다. */
function blankToNull(value: string): string | null {
  return value.trim() === '' ? null : value.trim()
}

function numberOrNull(value: string): number | null {
  return value.trim() === '' ? null : Number(value)
}
