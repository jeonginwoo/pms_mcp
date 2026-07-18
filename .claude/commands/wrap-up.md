---
description: 세션 마무리 — PROGRESS 갱신 + 커밋 + 브랜치 처리
---

세션을 마무리하라:

1. `docs/PROGRESS.md` 갱신:
   - "현재 상태" 섹션을 최신으로 (마일스톤, 다음 작업, 차단 요소)
   - 세션 로그 항목 추가 (완료/검증/미해결/다음 작업)
   - 이번 세션에 내린 설계 결정이 있으면 결정 기록 표에 추가
   - 작업 큐에 완료 체크(`- [x]`)·발견한 후속 일감(`- [ ]`) 반영
2. `docs/ROADMAP.md`의 완료 항목에 체크 표시.
3. 이번 세션에서 CLAUDE.md의 내용이 틀렸음을 발견했다면 (명령어 변경, 구조 변화 등) CLAUDE.md도 갱신. 레포 특유의 함정·반복 실수를 발견했으면 CLAUDE.md '교훈' 섹션에 한 줄 추가.
4. 커밋: `git add -A && git commit` (마일스톤 작업 `M<n>: ...`, 그 외 `chore:`/`docs:`) — 커밋 전 `git diff --staged`로 최종 확인.
5. **브랜치 처리** (docs/conventions/git-workflow.md):
   - 작업 브랜치라면: push 후 PR 생성(`gh pr create`)을 제안한다. 머지는 상대방 리뷰 승인 + CI green 후 squash로.
   - main이라면: 문서·하네스 경미 변경만 허용된다 — 코드 변경이 섞여 있으면 브랜치로 옮기는 것을 제안한다.
