# 재사용 시드 데이터

기존 프로젝트(`C:\Projects\pms_mcp`)에서 가져온 자산. 코드 재작성과 무관하게 유효.

- `people.json` — 사원 44명 (실 조직 구조 기반, 이름·이메일 가명화)
- `projects.json` — 프로젝트 382건 + 배정 (완료 319 · 진행중 34 · 수주확정 19 · 계약대기 10)

M0~M1에서 새 pms_back의 `src/main/resources/seed/`로 옮겨 사용한다.
원천: `PMS_MCP_기획/db/proten_projects.xlsx` (생성 스크립트는 기존 저장소 참조).
