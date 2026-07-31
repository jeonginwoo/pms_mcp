import { useState } from 'react'
import { useStore } from '../store.jsx'
import { StatusBadge, Bar, LogRow } from '../components/bits.jsx'
import { THIS_YM, ENGAGEMENT_LABEL, HQ_TEAM } from '../constants.js'

const GRID = 'minmax(0,2fr) minmax(70px,100px) 80px 64px minmax(94px,110px) minmax(80px,1fr) 46px'

export default function Projects() {
  const { S } = useStore()
  return S.selId ? <ProjectDetail /> : <ProjectList />
}

function ProjectList() {
  const st = useStore()
  const { S, setState, openProject, openCreate, visibleProjects, person, dday, fmtPeriod, hasPerm } = st

  const filt = visibleProjects().filter(p =>
    (S.projStatus === '전체' || p.status === S.projStatus) &&
    (!S.projKw || p.name.includes(S.projKw) || p.client.includes(S.projKw)))

  return (
    <section className="card">
      <div className="card-head">
        <h2>프로젝트 <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>{filt.length}건</span></h2>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <select value={S.projStatus} onChange={e => setState({ projStatus: e.target.value })}>
            {['전체', '계약대기', '수주확정', '진행중', '완료', '유지보수중'].map(s => <option key={s} value={s}>{s === '전체' ? '전체 상태' : s}</option>)}
          </select>
          <input placeholder="프로젝트 · 고객사 검색" value={S.projKw} onChange={e => setState({ projKw: e.target.value })} style={{ width: 220 }} />
          {hasPerm('createProj') && <button className="btn btn-primary" onClick={openCreate}>+ 새 프로젝트</button>}
        </div>
      </div>
      <div className="thead" style={{ gridTemplateColumns: GRID }}>
        <span>프로젝트</span><span>고객사</span><span>상태</span><span>담당 PM</span><span>기간</span><span>진행률</span><span style={{ textAlign: 'right' }}>마감</span>
      </div>
      {filt.map(p => {
        const dd = dday(p.end)
        return (
          <button key={p.id} className="trow" style={{ gridTemplateColumns: GRID, padding: '12px 2px' }} onClick={() => openProject(p.id)}>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontWeight: 700, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{p.name}</div>
              <div className="muted2" style={{ fontSize: 11.5 }}>{p.team}</div>
            </div>
            <span className="muted">{p.client}</span>
            <StatusBadge status={p.status} />
            <span className="muted">{person(p.pmId).name}</span>
            <span className="muted2" style={{ fontSize: 12 }}>{fmtPeriod(p)}</span>
            <Bar value={p.progress} done={p.status === '완료'} />
            <span style={{ fontSize: 11.5, color: dd.color, fontWeight: 600, textAlign: 'right' }}>{dd.text}</span>
          </button>
        )
      })}
      {filt.length === 0 && <div className="empty">조건에 맞는 프로젝트가 없습니다.</div>}
    </section>
  )
}

function ProjectDetail() {
  const st = useStore()
  const {
    S, setState, proj, person, canEdit, canManage, dday, fmtPeriod,
    openEdit, previewProgress, commitProgress, ASSIGNMENTS, MAINT,
  } = st
  const d = proj(S.selId)
  if (!d) return <div className="empty">프로젝트를 찾을 수 없습니다.</div>

  const dd = dday(d.end)
  const as = ASSIGNMENTS.filter(a => a.projectId === d.id && a.month === THIS_YM)
  const logs = (MAINT[d.id] || []).slice(0, 4)
  const editable = canEdit(d) && d.status === '진행중'
  const delta = S.editPercent - d.progress

  return (
    <div>
      <button onClick={() => setState({ selId: null, preview: null, saveMsg: null })}
        style={{ display: 'inline-flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', color: 'var(--muted)', fontSize: 12.5, padding: 0, marginBottom: 12 }}>
        ← 프로젝트 목록
      </button>
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1.7fr) minmax(320px,1fr)', gap: 16, alignItems: 'start' }}>
        <div style={{ display: 'grid', gap: 16 }}>
          <section className="card" style={{ padding: '22px 24px' }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 14, marginBottom: 16 }}>
              <div>
                <h2 style={{ margin: '0 0 4px', fontSize: 18, letterSpacing: '-.3px' }}>{d.name}</h2>
                <div className="muted" style={{ fontSize: 13 }}>{d.client} · {d.team} · PM {person(d.pmId).name}</div>
              </div>
              <div style={{ flex: 'none', display: 'flex', alignItems: 'center', gap: 8 }}>
                <StatusBadge status={d.status} big />
                {canManage(d) && <>
                  <button className="btn btn-ghost btn-sm" style={{ padding: '6px 12px', fontSize: 12 }} onClick={() => openEdit(S.selId)}>정보 수정</button>
                  <button className="btn btn-danger-ghost btn-sm" style={{ padding: '6px 12px', fontSize: 12 }} onClick={() => setState({ delConfirm: S.selId })}>삭제</button>
                </>}
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10, marginBottom: 12 }}>
              <div className="metric-box"><div className="k">기간</div><div className="v">{fmtPeriod(d)}</div></div>
              <div className="metric-box"><div className="k">마감까지</div><div className="v" style={{ color: dd.color }}>{dd.text}</div></div>
              <div className="metric-box"><div className="k">데이터 버전 (낙관적 락)</div><div className="v">v{d.version}</div></div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10, marginBottom: 18 }}>
              <div className="metric-box"><div className="k">계약 M/M</div><div className="v">{d.contractMm ? `${d.contractMm} MM` : '—'}</div></div>
              <div className="metric-box"><div className="k">수행 형태</div><div className="v">{ENGAGEMENT_LABEL[d.engagement] || '—'}</div></div>
              <div className="metric-box"><div className="k">계약 솔루션</div><div className="v">{d.solution || '—'}</div></div>
            </div>

            <div style={{ marginBottom: 6, display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
              <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>진행률</span>
              <span style={{ fontSize: 15, fontWeight: 800 }}>{d.progress}%</span>
            </div>
            {/* 진행률 바 — 드래그 편집(고스트 오버레이 + 마커) */}
            <div style={{ position: 'relative', background: 'var(--chip)', borderRadius: 12, height: 22, marginBottom: 18 }}>
              <div style={{ position: 'absolute', inset: 0, borderRadius: 12, overflow: 'hidden' }}>
                <div style={{ position: 'absolute', inset: '0 auto 0 0', width: `${d.progress}%`, background: 'linear-gradient(90deg,#3d63d8,#6f96ff)' }} />
                <div style={{ position: 'absolute', top: 0, bottom: 0, left: `${Math.min(d.progress, S.editPercent)}%`, width: `${Math.abs(delta)}%`, background: delta >= 0 ? 'rgba(61,99,216,.30)' : 'rgba(216,58,58,.28)' }} />
              </div>
              <div style={{ position: 'absolute', top: -3, bottom: -3, left: `${S.editPercent}%`, width: 2, background: 'var(--text)', borderRadius: 2, opacity: delta !== 0 ? .8 : 0, marginLeft: -1 }} />
              <input type="range" min="0" max="100" value={S.editPercent}
                onChange={e => setState({ editPercent: Math.max(0, Math.min(100, Number(e.target.value) || 0)), preview: null })}
                style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0, margin: 0, cursor: editable ? 'ew-resize' : 'default', pointerEvents: editable ? 'auto' : 'none' }} />
            </div>

            {editable ? (
              <div style={{ borderTop: '1px solid var(--border-soft)', paddingTop: 16 }}>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 10, flexWrap: 'wrap' }}>
                  <span className="muted" style={{ fontSize: 12, fontWeight: 600 }}>진행률 수정</span>
                  <span className="muted2" style={{ fontSize: 11.5 }}>위 진행률 바를 드래그하거나 값을 입력하세요 · 현재 {d.progress}% (v{d.version})</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <input type="number" min="0" max="100" value={S.editPercent}
                      onChange={e => setState({ editPercent: Math.max(0, Math.min(100, Number(e.target.value) || 0)), preview: null })}
                      style={{ width: 64, fontWeight: 700, fontSize: 14, textAlign: 'right', padding: '7px 8px' }} />
                    <span className="muted" style={{ fontSize: 13, fontWeight: 700 }}>%</span>
                    <span style={{ fontSize: 12.5, fontWeight: 700, color: delta === 0 ? 'var(--muted2)' : delta > 0 ? 'var(--ok)' : 'var(--danger)' }}>
                      {delta === 0 ? '변경 없음' : `${delta > 0 ? '+' : ''}${delta}%p`}
                    </span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    <div style={{ display: 'flex', gap: 6 }}>
                      {[25, 50, 75, 100].map(v => (
                        <button key={v} onClick={() => setState({ editPercent: v, preview: null })}
                          className="badge"
                          style={{ cursor: 'pointer', border: `1px solid ${S.editPercent === v ? 'var(--primary)' : 'var(--border)'}`, background: S.editPercent === v ? 'var(--primary-soft)' : 'var(--surface)', color: S.editPercent === v ? 'var(--primary)' : 'var(--muted)', padding: '5px 11px', borderRadius: 14 }}>
                          {v}%
                        </button>
                      ))}
                    </div>
                    <button className="btn btn-primary" disabled={delta === 0} style={{ opacity: delta === 0 ? .45 : 1 }}
                      onClick={async () => {
                        const r = await previewProgress(S.selId, S.editPercent) // 서버 확인 카드 요약 (confirmed=false)
                        if (r.ok) setState({ preview: `${r.summary} (v${d.version} 기준). 저장하시겠습니까?`, saveMsg: null })
                        else setState({ preview: null, saveMsg: { type: 'err', text: r.error } })
                      }}>
                      변경 검토 →
                    </button>
                  </div>
                </div>

                {S.preview && (
                  <div className="confirm-card" style={{ marginTop: 12 }}>
                    <div className="t">확인 필요 — 아직 저장되지 않았습니다</div>
                    <div style={{ fontSize: 13, marginBottom: 12 }}>{S.preview}</div>
                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                      <button className="btn btn-ghost" style={{ padding: '7px 14px', fontSize: 12.5 }} onClick={() => setState({ preview: null })}>취소</button>
                      <button className="btn btn-primary" style={{ padding: '7px 16px', fontSize: 12.5 }}
                        onClick={async () => {
                          const r = await commitProgress(S.selId, S.editPercent) // 서버 커밋 (403/409는 그대로 표시)
                          if (r.ok) setState({ preview: null, saveMsg: { type: 'ok', text: `저장되었습니다 — ${r.summary} (감사 로그 기록)` } })
                          else setState({ preview: null, saveMsg: { type: 'err', text: r.error } })
                        }}>
                        확인하고 저장
                      </button>
                    </div>
                  </div>
                )}
                {S.saveMsg && <div className={`save-msg ${S.saveMsg.type === 'ok' ? 'ok' : 'err'}`}>{S.saveMsg.text}</div>}
              </div>
            ) : d.status === '진행중' && (
              <div className="muted2" style={{ borderTop: '1px solid var(--border-soft)', paddingTop: 14, fontSize: 12.5 }}>
                진행률 수정은 해당 프로젝트 담당자(PM·배정 인원)만 가능합니다.
              </div>
            )}
          </section>

          {logs.length > 0 && (
            <section className="card" style={{ padding: '20px 24px' }}>
              <h3 style={{ marginBottom: 12, fontSize: 14.5 }}>최근 유지보수 이력</h3>
              {logs.map(l => <LogRow key={l.id} log={l} compact />)}
            </section>
          )}
        </div>

        <aside style={{ display: 'grid', gap: 16 }}>
          <AssignCard d={d} as={as} />
        </aside>
      </div>
    </div>
  )
}

/** 이번 달 배정 카드 — 관리 권한자는 M/M 인라인 수정·해제·추가 가능 (프로토타입 사양, 서버 반영) */
function AssignCard({ d, as }) {
  const st = useStore()
  const { people, person, canManage, utilOf, upsertAssign, updateAssignMm, removeAssign, showToast } = st
  const manage = canManage(d)
  const [add, setAdd] = useState(null) // { personId, mm } | null
  const totalMm = Math.round(as.reduce((s, a) => s + a.mm, 0) * 10) / 10

  // 후보: 전사 직속 제외 + 이번 달 미배정 인원 (가동률 표기)
  const candidates = people()
    .filter(p => p.team !== HQ_TEAM && !as.some(a => a.personId === p.id))
    .map(p => ({ id: p.id, label: `${p.name} · ${p.team} ${p.grade} (가동 ${utilOf(p.id, THIS_YM).adj}%)` }))

  const clampMm = (v) => Math.max(0.05, Math.min(1, Math.round((Number(v) || 0.05) * 100) / 100))
  const commitMm = (a, v) => {
    const mm = clampMm(v)
    if (mm !== a.mm) updateAssignMm(d.id, a.personId, a.month, mm)
  }
  const commitAdd = async () => {
    if (!add?.personId) { showToast('배정할 인원을 선택해 주세요'); return }
    const ok = await upsertAssign(d.id, Number(add.personId), THIS_YM, clampMm(add.mm))
    if (ok) setAdd(null)
  }

  const GRID = manage ? 'minmax(0,1fr) 44px 58px 22px' : 'minmax(0,1fr) 60px 50px'
  return (
    <section className="card" style={{ padding: '18px 20px' }}>
      <h3 style={{ marginBottom: 10 }}>이번 달 배정 <span className="muted2" style={{ fontWeight: 500, fontSize: 12 }}>{as.length}명 · {totalMm} M/M</span></h3>
      <div className="thead" style={{ gridTemplateColumns: GRID, padding: '6px 2px', fontSize: 11, gap: 8 }}>
        <span>담당자</span><span>직급</span><span style={{ textAlign: 'right' }}>M/M</span>{manage && <span />}
      </div>
      {as.map(a => {
        const p = person(a.personId)
        return manage ? (
          <div key={`${a.personId}-${a.month}`} className="trow" style={{ gridTemplateColumns: GRID, padding: '6px 2px', gap: 8 }}>
            <span style={{ fontWeight: 600, minWidth: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{p.name}</span>
            <span className="muted" style={{ fontSize: 12 }}>{p.grade}</span>
            <input key={a.mm} type="number" step="0.05" min="0.05" max="1" defaultValue={a.mm}
              onBlur={e => commitMm(a, e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') e.target.blur() }}
              style={{ fontSize: 12.5, fontWeight: 600, padding: '4px 6px', borderRadius: 7, textAlign: 'right', width: '100%' }} />
            <button title="배정 해제" onClick={() => removeAssign(d.id, a.personId, a.month)}
              style={{ background: 'none', border: 'none', color: 'var(--muted2)', fontSize: 15, lineHeight: 1, padding: 2 }}
              onMouseEnter={e => e.currentTarget.style.color = 'var(--danger)'}
              onMouseLeave={e => e.currentTarget.style.color = 'var(--muted2)'}>×</button>
          </div>
        ) : (
          <div key={`${a.personId}-${a.month}`} className="trow" style={{ gridTemplateColumns: GRID, padding: '9px 2px' }}>
            <span style={{ fontWeight: 600 }}>{p.name}</span>
            <span className="muted" style={{ fontSize: 12 }}>{p.grade}</span>
            <span style={{ textAlign: 'right', fontWeight: 600 }}>{a.mm}</span>
          </div>
        )
      })}
      {as.length === 0 && <div className="muted2" style={{ padding: '14px 0', fontSize: 12.5 }}>이번 달 배정이 없습니다.</div>}

      {manage && (add ? (
        <div style={{ display: 'grid', gap: 7, marginTop: 12, background: 'var(--soft)', border: '1px solid var(--border-soft)', borderRadius: 10, padding: 10 }}>
          <select value={add.personId} onChange={e => setAdd(s => ({ ...s, personId: e.target.value }))}
            style={{ fontSize: 12.5, padding: '6px 8px', borderRadius: 7, width: '100%' }}>
            <option value="">인원 선택…</option>
            {candidates.map(c => <option key={c.id} value={c.id}>{c.label}</option>)}
          </select>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
            <input type="number" step="0.05" min="0.05" max="1" value={add.mm}
              onChange={e => setAdd(s => ({ ...s, mm: e.target.value }))}
              style={{ fontSize: 12.5, fontWeight: 600, padding: '6px 8px', borderRadius: 7, width: 70, textAlign: 'right' }} />
            <span className="muted" style={{ fontSize: 11.5, fontWeight: 600 }}>M/M</span>
            <span style={{ flex: 1 }} />
            <button className="btn btn-primary btn-sm" onClick={commitAdd}>배정</button>
            <button className="btn btn-ghost btn-sm" onClick={() => setAdd(null)}>취소</button>
          </div>
        </div>
      ) : (
        <button onClick={() => setAdd({ personId: '', mm: '0.2' })}
          style={{ marginTop: 12, width: '100%', background: 'var(--surface)', border: '1px dashed var(--border)', color: 'var(--primary)', borderRadius: 9, padding: 8, fontSize: 12.5, fontWeight: 600 }}>
          + 인원 배정
        </button>
      ))}
    </section>
  )
}
