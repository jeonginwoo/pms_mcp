# Git 협업 워크플로 (2인 + AI 에이전트)

> 모델: **GitHub Flow** (main + 단기 브랜치). 2인·바이브코딩 규모에 Git Flow(develop/release 브랜치)는 과잉이다.
> 이 파일은 CLAUDE.md를 통해 매 세션 주입된다 — AI 에이전트도 사람과 같은 규칙을 따른다.

## 1. 브랜치 규칙

- **main은 항상 그린**: main 직접 커밋 금지. 예외 — 문서(`docs:`)·하네스(`chore:`)의 경미한 변경은 main 직접 커밋 허용.
- **1 작업 = 1 브랜치 = 1 PR**: ROADMAP 체크 항목 1개(크면 쪼갠 단위)가 브랜치 하나. 브랜치 수명은 1~2일을 넘기지 않는다 — 길어지면 작업을 더 쪼갠 것이 아니라는 신호.
- **명명**: `<type>/<슬러그>`. type은 `feat`/`fix`/`chore`/`docs`, 마일스톤 작업은 슬러그에 마일스톤 포함 — 예: `feat/m1-get-utilization`, `fix/m1-visibility-404`.
- **시작 절차**: `git switch main && git pull --rebase && git switch -c feat/...`
- **AI 에이전트(Claude)는 main에서 코드 작업을 하지 않는다.** 세션 시작 시 브랜치를 확인하고, main이면 분기부터 한다. `ralph.sh`도 작업 브랜치에서만 돈다.

## 2. PR·머지 규칙

- **머지 방식: squash merge** — 바이브코딩의 자잘한 WIP 커밋을 뭉쳐 main에는 "1 작업 = 1 커밋"만 남긴다. squash 커밋 메시지는 커밋 규칙(`M1: ...` / `chore:` / `docs:`)을 따른다.
- **머지 조건**: CI(`.github/workflows/ci.yml`) green + **상대방 리뷰 승인 1건**. 문서·하네스만 바꾼 PR은 CI green이면 셀프 머지 허용.
- PR 올리기 전 `git pull --rebase origin main`으로 최신화. 머지 후 브랜치는 삭제한다.
- 리뷰 부담을 줄이기 위해 PR 설명에 `boundary-reviewer` 에이전트 판정과 `/verify` 결과를 붙인다.

## 3. 충돌 예방 (2인 분담)

- **분담 단위 = Modulith 모듈**: 같은 모듈의 `internal/`을 두 사람이 동시에 만지지 않는다. 작업을 잡을 때 서로 다른 모듈을 잡는 것이 기본값.
- **공유 파일**(CLAUDE.md·컨벤션·모듈 공개 API·PROGRESS.md)을 바꿀 때는 상대에게 먼저 알린다.
- **PROGRESS.md 충돌 시**: 세션 로그는 각자 항목을 둘 다 보존(append), "현재 상태"는 최신 세션이 이긴다.

## 4. 금지

- main에 force push 금지 (settings.json이 에이전트의 `git push --force`를 차단한다).
- 개인 브랜치 force push는 `--force-with-lease`만, 상대가 그 브랜치를 쓰지 않는 것이 확실할 때만.
- 리뷰·CI 통과를 위해 테스트를 약화·삭제하는 것 금지 — 실패는 코드를 고쳐서 푼다.
