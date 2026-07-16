# React / TypeScript Conventions (frontend/)

> 대상: 챗 위젯 및 PMS 프론트. 기존 프로토타입(`frontend/`)은 JS로 작성되어 있다 — **새 파일은 TS로 작성**하고, 기존 파일은 수정할 때 점진 전환한다.

## 1. 컴포넌트
- **함수형 컴포넌트만** 사용. 클래스 컴포넌트 금지.
- 파일 하나 = 컴포넌트 하나. 파일명은 PascalCase (`ChatPanel.tsx`).
- Props는 `interface Props`로 선언, `export default function ChatPanel({ ... }: Props)`.
- 컴포넌트가 150줄을 넘으면 분리를 검토한다.

## 2. 타입
- `any` 금지. 불가피하면 `unknown` + 좁히기(narrowing).
- API 응답 타입은 `src/types/`에 모으고 서버 DTO와 이름을 맞춘다 (Ubiquitous Language).
- 타입 단언(`as`)은 최소화 — 타입 가드 함수를 우선한다.

## 3. 상태·데이터
- 지역 상태는 `useState`/`useReducer`, 서버 상태는 fetch 래퍼(`src/api.ts`) 경유 — 컴포넌트에서 직접 `fetch` 호출 금지.
- 전역 상태는 기존 store 패턴(`store.jsx`)을 따르고, 새 라이브러리 도입은 `기술_선택_근거.md` 절차를 따른다.
- `useEffect` 의존성 배열 생략 금지. lint 경고를 억제하지 않는다.

## 4. 챗 위젯 특수 규칙 (PRD FR-AI 계열)
- 입력 2,000자 초과 시 전송 버튼 비활성 + 안내 — **서버 도달 전 차단** (FR-AI-02).
- 쓰기 작업은 반드시 확인 카드([실행]/[취소]) 렌더링 — 카드 없는 쓰기 경로를 만들지 않는다 (FR-AI-04).
- 응답 대기 중 진행 상태 표시 + 중단 버튼 (FR-AI-03).
- 모든 응답에 👍/👎 피드백 버튼, 👎는 사유 선택 (FR-AI-05).
- 도구 결과·모델 응답은 텍스트로 렌더 — `dangerouslySetInnerHTML` 금지 (인젝션 방어).

## 5. 스타일·품질
- 기존 `styles.css`/`theme.js` 체계를 따른다. 인라인 스타일은 동적 값에만.
- ESLint 경고 0 유지. `eslint-disable`은 사유 주석 필수.
- 테스트: 로직(파서·상태 전이)은 Vitest 단위 테스트. 확인 카드 흐름(FR-AI-04)은 컴포넌트 테스트 필수.
