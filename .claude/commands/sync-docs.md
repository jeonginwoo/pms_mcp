---
description: 결정 변경 후 문서 동기화 — CLAUDE.md 우선, drift 제거
---

설계·컨벤션 결정이 바뀌었다: $ARGUMENTS

1. 옛 결정이 기록된 곳을 전부 찾는다: `CLAUDE.md`, `docs/conventions/*`, `기술_선택_근거.md`, `PMS_MCP_구현_가이드.md`, `PMS_AI기능_PRD.md`, `docs/ROADMAP.md`, `docs/evals/eval-cases.md`.
2. **CLAUDE.md를 먼저 갱신한다** — 운영 문서이자 에이전트가 매 세션 읽는 진실의 서열 1위. 낡은 문서를 물리적으로 못 고치는 경우(외부 참고자료 등)에는 CLAUDE.md에 "무엇이 무엇을 대체하는가"를 명시한다.
3. 나머지 문서를 각 문서의 기존 형식을 유지하며 갱신한다 (날짜 있는 개정 주석 포함).
4. `docs/PROGRESS.md` 결정 기록 표에 날짜·결정·근거를 추가한다.
5. AI 도구·프롬프트를 건드리는 결정이면: **도구명·도구 설명·시스템 프롬프트·eval셋은 four-in-one** — 넷을 함께 갱신하고 전체 eval 재실행을 계획에 포함한다.
6. 커밋: `docs: <결정> 반영`.
