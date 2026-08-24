/*
 * 조직 트리 표시 순서 (US-E3 화면 · 2026-08-24 신설).
 *
 * 서버는 `GET /api/org-units`을 **정렬 없이** 준다(`findAll()`). 화면이 들여쓰기만 하고
 * 순서를 정하지 않으면 새로 만든 노드가 목록 맨 끝에 붙어 **부모와 떨어져 보인다** —
 * 트리처럼 보이지 않는다는 검수 지적(2026-08-24)이 이것이었다. 정렬을 서버에 두지 않은
 * 이유는 이 순서가 **표시 규칙**이라서다: 같은 목록을 조직 관리 패널과 소속 선택
 * 드롭다운이 함께 쓰고, 후자는 들여쓴 한 줄짜리 라벨이 필요하다. 두 화면이 각자 트리를
 * 재구성하면 순서가 갈리므로 여기 한 곳에 둔다.
 *
 * 형제 정렬은 **이름순**이다. 조직도의 표시 순서를 그대로 재현하려면 명시 정렬 컬럼이
 * 필요한데, 그 값을 유지할 화면·API가 없으므로 예측 가능한 규칙을 택했다.
 */
import type { OrgUnitView } from './types/api'

export interface OrgTreeRow {
  unit: OrgUnitView
  /** 회사(root) = 0 — 들여쓰기 폭에 곱해 쓴다 */
  depth: number
}

/** 부모 → 자식 순(DFS)으로 펼친다. 부모를 못 찾는 노드는 뒤에 붙여 잃지 않는다. */
export function flattenOrgTree(units: OrgUnitView[]): OrgTreeRow[] {
  const byParent = new Map<number | null, OrgUnitView[]>()
  units.forEach((unit) => {
    const siblings = byParent.get(unit.parentId) ?? []
    siblings.push(unit)
    byParent.set(unit.parentId, siblings)
  })
  byParent.forEach((siblings) => siblings.sort((a, b) => a.name.localeCompare(b.name)))

  const rows: OrgTreeRow[] = []
  // 방문 표시는 순환 방어다 — 지금은 부모를 바꾸는 경로가 없어 순환이 생길 수 없지만,
  // 그 라우트가 서는 날 이 재귀가 스택을 넘기는 것으로 알게 되면 늦다
  const seen = new Set<number>()
  const walk = (parentId: number | null, depth: number) => {
    (byParent.get(parentId) ?? []).forEach((unit) => {
      if (seen.has(unit.id)) {
        return
      }

      seen.add(unit.id)
      rows.push({ unit, depth })
      walk(unit.id, depth + 1)
    })
  }
  walk(null, 0)

  // 고아 노드 — 부모가 가시성 밖이거나 데이터가 깨진 경우다. 조용히 사라지면
  // 관리자가 그 노드를 손볼 방법이 없어지므로 평평하게라도 내놓는다
  units.filter((unit) => !seen.has(unit.id))
    .forEach((unit) => rows.push({ unit, depth: 0 }))

  return rows
}

/**
 * 그 노드와 그 아래 전부의 id — 이동 대상 셀렉트에서 **빼야 하는 집합**이다(AC E3-6).
 *
 * 서버가 같은 규칙으로 400을 내지만(순환 금지) 고를 수 없게 하는 편이 낫다: 고르고 나서
 * 거절당하는 경로는 사용자가 규칙을 오류 문구로 배우게 만든다.
 */
export function subtreeIds(units: OrgUnitView[], rootId: number): Set<number> {
  const childrenOf = new Map<number, OrgUnitView[]>()
  units.forEach((unit) => {
    if (unit.parentId === null) {
      return
    }

    const siblings = childrenOf.get(unit.parentId) ?? []
    siblings.push(unit)
    childrenOf.set(unit.parentId, siblings)
  })

  const ids = new Set<number>([rootId])
  const queue = [rootId]

  while (queue.length > 0) {
    const current = queue.shift() as number
    ;(childrenOf.get(current) ?? []).forEach((child) => {
      if (!ids.has(child.id)) {
        ids.add(child.id)
        queue.push(child.id)
      }
    })
  }

  return ids
}

/** 드롭다운용 한 줄 라벨 — 계층을 들여쓰기로 보여 준다(`<option>`은 마크업이 안 된다). */
export function orgOptionLabel(row: OrgTreeRow): string {
  return row.depth === 0 ? row.unit.name : `${' '.repeat(row.depth * 3)}└ ${row.unit.name}`
}
