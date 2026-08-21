/*
 * 인력 · 조직 — 목록은 서버가 화자의 가시성 범위로 걸러 준 것이다
 * (관리자=전사 · 부문장=부문 · 팀장·팀원=소속 팀 subtree — 2026-08-22 팀원 scope 변경).
 *
 * 관리 권한("사용자/조직/권한 관리" 플래그 — 기본 그룹 중 관리자만)이 있을 때만
 * 등록·삭제·조직 관리 UI가 보인다. 삭제는 서버에서 soft 비활성이다(E2-3) — 과거
 * 배정·감사 이력이 이 인원을 가리키고 있어 행을 지울 수 없다. 수정(E2-2)은 아직 없다.
 */
import { useMemo, useState } from 'react'
import { useStore } from '../store'
import { Empty, ErrorText } from '../components/ui'
import OrgUnitPanel from '../components/OrgUnitPanel'
import PersonCreateModal from '../components/PersonCreateModal'

const GRID = 'minmax(0,1.2fr) minmax(0,1.4fr) minmax(80px,110px) 64px'

export default function People() {
  const { me, people, deactivatePerson, showToast } = useStore()
  const [keyword, setKeyword] = useState('')
  const [pending, setPending] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
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
            {manageable && person.id !== me?.id && (
              pending === person.id ? (
                <span style={{ display: 'flex', gap: 4 }}>
                  <button className="btn btn-danger btn-sm"
                    onClick={() => void runDeactivate(person.id, person.name)}>확인</button>
                  <button className="btn btn-ghost btn-sm"
                    onClick={() => setPending(null)}>취소</button>
                </span>
              ) : (
                <button className="btn btn-danger-ghost btn-sm"
                  title="삭제 — 로그인 차단·목록 제외(과거 배정·감사 이력은 보존된다)"
                  onClick={() => { setPending(person.id); setError(null) }}>
                  삭제
                </button>
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

      {manageable ? <OrgUnitPanel /> : <OrgUnitSummary />}
      {creating && <PersonCreateModal onClose={() => setCreating(false)} />}
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
