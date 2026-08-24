-- 수신자별 알림 설정 (AC H1-4 · F1-5 필터의 저장소) — 2026-08-24.
--
-- **단위는 `NotificationType`이다**: H1-4가 `{progress, project, org, weekly}` 네 칸으로
-- 적혀 있었지만 그것은 알림 유형이 정해지기 전의 문구이고, 넷 중 `project` 말고는
-- 대응하는 알림이 없다(`weekly`는 유형이 아니라 주기다). `NotificationType`의 javadoc이
-- 이미 "수신자의 알림 설정이 켜고 끄는 단위"라고 스스로 선언하고 있어 그쪽에 맞춘다
-- (PRD-pms §6 H1-4 문구를 함께 고쳤다).
--
-- **끈 것만 저장한다**(opt-out): 행이 없으면 켜진 것이다. 유형이 늘 때마다 44명 × N행을
-- 채워 넣지 않아도 되고, 새 유형이 기본 켜짐으로 자연히 들어온다. 대신 "명시적으로
-- 켰다"와 "설정한 적 없다"를 구분하지 않는다 — H1-4에 그 구분이 필요한 AC가 없다.
--
-- notification이 소유한다. auth의 `User`에 컬럼을 더하지 않는 이유는 필터를 거는 쪽이
-- notification이고, 거기서 auth를 읽으면 모듈 경계를 하나 더 넓히기 때문이다
-- (`V2__users.sql`이 "알림 모듈이 생길 때 추가한다"고 미뤄 둔 것을 여기서 이행한다).
create table notification_mutes (
    person_id bigint      not null,
    type      varchar(40) not null,
    primary key (person_id, type)
);
