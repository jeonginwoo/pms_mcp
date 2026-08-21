/*
 * 상세 → 정보 수정 요청 본문.
 *
 * `PUT /projects/{id}`는 전체 치환(A5)이라 바꾸지 않는 필드도 현재 값을 그대로 실어야
 * 한다 — 빼먹으면 지워진다. 그 매핑을 한 곳에 두는 이유는 호출부가 둘이기 때문이다:
 * 정보 수정 모달과 상태 전이 버튼이 같은 라우트를 쓴다.
 */
import type { EditProjectBody, ProjectDetail } from './types/api'

export function editBodyOf(
    detail: ProjectDetail,
    overrides: Partial<EditProjectBody> = {}): EditProjectBody {
  return {
    client: detail.client,
    name: detail.name,
    solution: detail.solution,
    engagement: detail.engagement,
    contractMm: detail.contractMm,
    startDate: detail.startDate,
    endDate: detail.endDate,
    status: detail.status,
    version: detail.version,
    ...overrides,
  }
}
