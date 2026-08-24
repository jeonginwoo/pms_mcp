/*
 * 인력 · 조직 — 목록은 서버가 화자의 가시성 범위로 걸러 준 것이다
 * (관리자=전사 · 부문장=부문 · 팀장·팀원=소속 팀 subtree — 2026-08-22 팀원 scope 변경).
 *
 * 관리 권한("사용자/조직/권한 관리" 플래그 — 기본 그룹 중 관리자만)이 있을 때만
 * 등록·수정·삭제·조직/직급/권한 그룹 관리 UI가 보인다. 삭제는 서버에서 soft
 * 비활성이다(E2-3) — 과거 배정·감사 이력이 이 인원을 가리키고 있어 행을 지울 수 없다.
 *
 * 부록 A는 이 기능들을 `/settings` 3탭에 두지만 이 앱은 인력·조직을 `/people`에
 * 두고 감사만 별 항목으로 뒀다(2026-08-24 결정 · PRD-pms §12 미해결 등재).
 * 직급·권한 그룹 관리는 그 배치를 따라 조직 패널 아래에 붙였다 — 부록 A의
 * "조직 관리 탭"이 조직 트리(좌)와 직급·권한 그룹(우)을 한 화면에 두는 구성이다.
 */
import { useMemo, useState } from 'react'
import { useStore } from '../store'
import { Empty, ErrorText } from '../components/ui'
import GradePanel from '../components/GradePanel'
import OrgUnitPanel from '../components/OrgUnitPanel'
import PermissionGroupPanel from '../components/PermissionGroupPanel'
import PersonCreateModal from '../components/PersonCreateModal'
import PersonEditModal from '../components/PersonEditModal'
import type { PersonSummary } from '../types/api'

const GRID = 'minmax(0,1.2fr) minmax(0,1.4fr) minmax(80px,110px) 116px'

export default function People() {
  const { me, people, deactivatePerson, showToast } = useStore()
  const [keyword, setKeyword] = useState('')
  const [pending, setPending] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<PersonSummary | null>(null)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const manageable = me?.manageOrg === true

  const filtered = useMemo(() => {
    const needle = keyword.trim()

    if (needle === '') {
      return people
    }

    return people.filter((person) =>
      person.name.includes(needle)
      || person.orgUnit.includes(needle)
      || person.grade.includes(needle))
  }, [people, keyword])

  const runDeactivate = async (personId: number, name: string) => {
    const result = await deactivatePerson(personId)
    setPending(null)

    if (result.ok) {
      setError(null)
      showToast(`${name}님을 삭제했습니다 (비활성 처리)`)

      return
    }

    setError({ code: result.error.code, message: result.error.message })
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,2fr) minmax(260px,1fr)', gap: 16, alignItems: 'start' }}>
      <section className="card">
        <div className="card-head">
          <h2>
            인력{' '}
            <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>
              {filtered.length}명
            </span>
          </h2>
          <div style={{ display: 'flex', gap: 8 }}>
            <input placeholder="이름 · 조직 · 직급 검색" value={keyword}
              onChange={(e) => setKeyword(e.target.value)} style={{ width: 220 }} />
            {manageable && (
              <button className="btn btn-primary" onClick={() => setCreating(true)}>
                + 인력 등록
              </button>
            )}
          </div>
        </div>

        <div className="thead" style={{ gridTemplateColumns: GRID }}>
          <span>이름</span>
          <span>소속</span>
          <span>직급</span>
          <span />
        </div>
        {filtered.map((person) => (
          <div key={person.id} className="trow" style={{ gridTemplateColumns: GRID }}>
            <span style={{ fontWeight: 600 }}>{person.name}</span>
            <span className="muted">{person.orgUnit}</span>
            <span className="muted">{person.grade}</span>
            {manageable && (
              pending === person.id ? (
                <span style={{ display: 'flex', gap: 4 }}>
                  <button className="btn btn-danger btn-sm"
                    onClick={() => void runDeactivate(person.id, person.name)}>확인</button>
                  <button className="btn btn-ghost btn-sm"
                    onClick={() => setPending(null)}>취소</button>
                </span>
              ) : (
                <span style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                  <button className="btn btn-ghost btn-sm"
                    title="이름·소속·직급·권한 그룹 수정 (E2-2·E1-1)"
                    onClick={() => { setEditing(person); setError(null) }}>
                    수정
                  </button>
                  {/* 본인 삭제는 서버가 422로 막는다 — 버튼 자체를 두지 않는다 */}
                  {person.id !== me?.id && (
                    <button className="btn btn-danger-ghost btn-sm"
                      title="삭제 — 로그인 차단·목록 제외(과거 배정·감사 이력은 보존된다)"
                      onClick={() => { setPending(person.id); setError(null) }}>
                      삭제
                    </button>
                  )}
                </span>
              )
            )}
          </div>
        ))}
        {filtered.length === 0 && <Empty>조건에 맞는 인력이 없습니다.</Empty>}

        {error && (
          <div style={{ marginTop: 12 }}>
            <ErrorText code={error.code} message={error.message} />
          </div>
        )}

        <div className="muted2" style={{ fontSize: 11.5, marginTop: 12 }}>
          가시성 범위 안의 인원만 보입니다 — 범위 밖은 목록에 없습니다(404 은닉과 같은 규칙).
          {manageable && ' 삭제는 soft 비활성입니다(E2-3) — 로그인·목록에서 빠지고 과거 이력은 남습니다.'}
        </div>
      </section>

      {manageable ? (
        <div style={{ display: 'grid', gap: 16 }}>
          <OrgUnitPanel />
          <GradePanel />
          <PermissionGroupPanel />
        </div>
      ) : <OrgUnitSummary />}

      {creating && <PersonCreateModal onClose={() => setCreating(false)} />}
      {editing && (
        <PersonEditModal person={editing} onClose={() => setEditing(null)} />
      )}
    </div>
  )
}

/** 관리 권한이 없을 때의 소속별 인원 — 가시성 범위 안에서 센다. */
function OrgUnitSummary() {
  const { people } = useStore()
  const byOrgUnit = useMemo(() => {
    const groups = new Map<string, number>()
    people.forEach((person) => groups.set(person.orgUnit, (groups.get(person.orgUnit) ?? 0) + 1))

    return [...groups.entries()].sort((a, b) => b[1] - a[1])
  }, [people])

  return (
    <section className="card">
      <div className="card-head"><h3>소속별 인원</h3></div>
      <div style={{ display: 'grid', gap: 8 }}>
        {byOrgUnit.map(([orgUnit, count]) => (
          <div key={orgUnit} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5 }}>
            <span>{orgUnit}</span>
            <span style={{ fontWeight: 700 }}>{count}</span>
          </div>
        ))}
        {byOrgUnit.length === 0 && <span className="muted2" style={{ fontSize: 12.5 }}>—</span>}
      </div>
      <div className="muted2" style={{ fontSize: 11.5, marginTop: 12 }}>
        가시성 범위 기준 집계입니다.
      </div>
    </section>
  )
}
