-- 프로젝트별 권한 커스텀 (2026-08-26) — US-A8 / 상위 PRD §4-2 "프로젝트별 권한 커스텀".
--
-- **이 표는 기본값과 다른 칸만 담는다**. AC A8-2가 "기본값과 같은 값은 저장하지
-- 않는다(해당 override 행 삭제)"를 명시하므로, 행이 없다 = 그 칸은 §4-2 기본값이다.
-- 그래서 382건 대부분이 행 0개이고, `overrides: []`(전체 기본값 복원)는 삭제로 끝난다.
-- 기본값을 전부 적재해 두는 방식이었다면 §4-2 표가 바뀌는 날 이미 저장된 값이
-- 옛 기본값을 그대로 들고 있어 **표와 데이터가 조용히 갈린다**.
--
-- **조정 가능한 칸은 8개뿐이다**: {PL, PARTICIPANT} × {EDIT_INFO, ASSIGN, PROGRESS,
-- COMPLETE_REOPEN}. PM 열 전체와 HANDOVER 행은 고정이라(§4-2 — 매트릭스를 고치는
-- 역할이 스스로 잠기면 복구 불가하고, 이관은 비가역 행위의 안전장치다) 여기에
-- 들어올 수 없다. 그 판정은 애플리케이션이 422로 막는다 — 검사 제약을 SQL에도
-- 두면 §4-2가 바뀔 때 고칠 자리가 둘이 된다.
create table project_permission_overrides (
    id          bigserial    primary key,
    project_id  bigint       not null references projects (id),
    role        varchar(20)  not null,
    action      varchar(30)  not null,
    allowed     boolean      not null,

    -- 한 칸은 한 값이다 — 같은 (프로젝트, 역할, 기능)에 두 행이 있으면
    -- 어느 것이 유효한지 코드가 정해야 하고, 그 규칙은 어디에도 없다
    constraint uq_project_permission_cell unique (project_id, role, action)
);

-- 판정 경로의 질의는 언제나 "이 프로젝트의 override 전부"다(칸 단위로 묻지 않는다):
-- 매트릭스 병합이 8칸을 한꺼번에 세우므로 한 번 읽어 메모리에서 겹친다.
create index ix_project_permission_project
    on project_permission_overrides (project_id);
