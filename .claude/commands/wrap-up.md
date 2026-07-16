---
description: 세션 마무리 — PROGRESS 갱신 + 커밋
---

세션을 마무리하라:

1. `docs/PROGRESS.md` 갱신:
   - "현재 상태" 섹션을 최신으로 (마일스톤, 다음 작업, 차단 요소)
   - 세션 로그 항목 추가 (완료/검증/미해결/다음 작업)
   - 이번 세션에 내린 설계 결정이 있으면 결정 기록 표에 추가
2. `docs/ROADMAP.md`의 완료 항목에 체크 표시.
3. 이번 세션에서 CLAUDE.md의 내용이 틀렸음을 발견했다면 (명령어 변경, 구조 변화 등) CLAUDE.md도 갱신.
4. 커밋: `git add -A && git commit -m "M<n>: <작업 요약>"` — 커밋 전 `git diff --staged`로 최종 확인.
