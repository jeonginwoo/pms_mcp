---
description: 다음 작업 시작 — PROGRESS 확인 후 계획 제시 (--loop는 ralph.sh 자율 모드)
---

다음 순서로 진행하라:

1. **브랜치 확인**: `git branch --show-current`. 코드 작업인데 main이면 `docs/conventions/git-workflow.md` 규칙대로 작업 브랜치를 만든다 (문서·하네스만 만지는 세션은 main 유지 가능).
2. `docs/PROGRESS.md`를 읽고 현재 상태·다음 작업·작업 큐를 파악한다.
3. `docs/ROADMAP.md`에서 해당 마일스톤의 체크리스트와 게이트를 확인한다.
4. 관련 문서의 해당 섹션을 읽는다 (구현 작업이면 `PMS_MCP_구현_가이드.md`의 해당 Step, 요구사항이면 PRD). 두 모듈 이상에 걸치는 설계면 `architect` 에이전트를 먼저 사용한다.
5. **코드를 작성하기 전에** 작업 계획(변경 파일, 접근 방식, 검증 방법)을 제시하고 승인을 받는다.
6. CLAUDE.md의 구조적 원칙 6가지를 위반하지 않는지 계획 단계에서 스스로 점검한다.

$ARGUMENTS

## 루프 모드 (`--loop` — ralph.sh 전용)

$ARGUMENTS에 `--loop`가 있으면 위 5번의 승인 대기를 생략하고, 작업 큐의 **첫 미완 항목 1개**를 끝까지 완료한다:

1. `docs/PROGRESS.md` 작업 큐에서 첫 `- [ ]` 항목 선택. `(blocked: ...)` 항목은 건너뛰고 사유를 보고한다.
2. TDD로 구현한다 (Red→Green→Refactor, 컨벤션 준수).
3. `bash scripts/verify.sh` 통과 확인. 경계·도메인·보안 변경이면 `boundary-reviewer` 에이전트로 diff를 검토받는다.
4. 영속화: 작업 큐 항목을 `- [x]` + 한 줄 노트로 갱신. 작업 중 발견한 후속 일감은 새 `- [ ]`로 **추가만 한다** (몰래 실행 금지 — scope creep 차단). `git add -A && git commit` (커밋 규칙 준수).
5. 학습: 레포 특유의 함정·반복 실수를 발견했으면 CLAUDE.md '교훈' 섹션에 한 줄 추가 (같은 커밋).
6. 2~3문장으로 보고하고 **STOP** — 다음 작업으로 자동 진행 금지 (다음 반복은 ralph.sh가 새 세션으로 시작한다).
