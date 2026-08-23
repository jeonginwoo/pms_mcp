package kr.proten.pms.person;

/**
 * 화자 본인의 신원 — 팀·부문·권한 그룹명 (상위 PRD §4-3, FR-AI-16).
 *
 * `MeView`(화면용)와 따로 두는 이유가 이 레코드의 존재 이유다: 화면은 권한 플래그를
 * 받아 버튼을 정리하지만, **챗은 유효 권한을 받지 않는다**(2026-08-03 결정). 플래그를
 * 담은 표현을 그대로 밖으로 넘기면 그 결정이 필드 하나로 무너지므로, 담지 않는 표현을
 * 따로 만들어 컴파일 시점에 못을 박는다.
 *
 * team·division을 함께 담는 이유: 조직은 트리 상 위치의 파생 개념이라(PRD-pms §4)
 * 소속 노드 이름 하나로는 "어느 부문 사람인가"를 답할 수 없고, 트리 탐색은 person
 * 안에서 끝나야 한다(다른 모듈은 조직 구조를 모른다).
 *
 * @param team     소속 조직 노드 이름
 * @param division 소속 경로상 최상위 부문 이름 — 부문 직속이면 team과 같다
 */
public record PersonIdentity(
        Long id,
        String name,
        String team,
        String division,
        String permissionGroup) {
}
