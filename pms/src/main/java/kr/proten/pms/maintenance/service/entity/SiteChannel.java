package kr.proten.pms.maintenance.service.entity;

/**
 * 사이트 유입 채널 — OEM 채널 계약은 원천 프로젝트가 없다는 것이 US-D2(직접 등록
 * 입구)의 근거다. 시드 157사이트 중 지정된 것은 43건뿐이라 값이 없는 사이트가 정상이다.
 */
public enum SiteChannel {
    OEM("OEM"),
    ENT("ENT");

    private final String label;

    SiteChannel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
