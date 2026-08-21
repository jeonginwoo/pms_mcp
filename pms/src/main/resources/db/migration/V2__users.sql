-- 로그인 계정 (PRD-pms §4 User) — 2026-08-21 인증 도입분.
-- Person과 1:1이며 로그인 ID는 email이다(§3). 모듈 간 물리 FK는 걸지 않는다.
-- notifPrefs(§4)는 알림 모듈이 생길 때 추가한다 — 지금 쓰는 곳이 없다.
create table users (
    id            bigint       not null primary key,
    person_id     bigint       not null unique,
    email         varchar(200) not null unique,
    password_hash varchar(100) not null,
    phone         varchar(40),
    version       bigint       not null default 0
);
