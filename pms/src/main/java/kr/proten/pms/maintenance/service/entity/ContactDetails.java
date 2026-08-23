package kr.proten.pms.maintenance.service.entity;

/**
 * 연락처에서 뽑아낸 조각 (VO) — 어느 것이든 null일 수 있다.
 * 파싱은 {@code service/impl}의 몫이고 엔티티는 결과만 받는다.
 */
public record ContactDetails(String name, String title, String phone, String email) {
    public static ContactDetails empty() {
        return new ContactDetails(null, null, null, null);
    }
}
