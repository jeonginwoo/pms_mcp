-- 이슈 쓰기 확장 (2026-08-26) — 제목·유형·본문 수정 · soft 삭제 · 코멘트 수정
-- (AC D3-5·D3-6·D3-7 신설 · AC D3-3 append-only 폐기 — 사용자 결정, PROGRESS 결정 기록).
--
-- 착수 계기는 실측된 공백이다: `PATCH /issues/{id}`가 status·assigneeId **두 칸만**
-- 받아 **제목 오타를 고칠 방법이 아예 없었다**. 등록 경로는 있는데 정정 경로가 없는
-- 화면이었다.

-- 본문 — 지금까지 이슈는 제목만 있었고 내용은 코멘트가 졌다. nullable인 것은
-- 시드 267건이 본문 없이 적재됐기 때문이고, 그 상태가 정상이다(빈 문자열로
-- 채우면 "안 쓴 것"과 "지운 것"을 구분할 수 없다).
alter table maintenance_issues add column content text;

-- 등록자 — **없어서 신설한다**. 수정·삭제 권한이 "등록자·담당자 + 계약 관리 플래그"
-- (사용자 결정)인데 이 표에는 담당자만 있었고 누가 올렸는지는 어디에도 없었다.
-- nullable인 것도 시드 때문이다: 구 게시판 데이터는 작성자를 남기지 않았으므로
-- 시드 이슈의 정정은 담당자나 관리 플래그 보유자가 든다.
alter table maintenance_issues add column reporter_id bigint;

-- soft 삭제 — 프로젝트 A4 선례를 따른다(hard delete가 아니다). 이 앱은 이력 보존이
-- 원칙이고(계약은 삭제 API 자체가 없다 — D2), 행을 지우면 코멘트와 감사가 가리키는
-- 대상이 사라진다. 조회는 전부 이 플래그로 걸러진다.
alter table maintenance_issues add column deleted boolean not null default false;

-- 목록은 삭제분을 빼고 읽는다 — D3-4의 "내 담당 열린 이슈"가 여기를 지난다
drop index ix_issue_assignee_status;
create index ix_issue_assignee_status on maintenance_issues (assignee_id, status)
    where deleted = false;

-- 코멘트 수정 시각 — append-only를 폐기하면서 **고쳐졌다는 사실은 남긴다**
-- (사용자 결정은 "본인 것만 수정·삭제"이고 tombstone은 미채택이지만, 수정 흔적을
-- 지우는 것까지 요구한 것은 아니다). null = 한 번도 고치지 않았다.
alter table issue_comments add column updated_at timestamp with time zone;
