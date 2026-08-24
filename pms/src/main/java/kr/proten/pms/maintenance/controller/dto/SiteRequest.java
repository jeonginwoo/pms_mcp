package kr.proten.pms.maintenance.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.service.dto.ContactCommand;
import kr.proten.pms.maintenance.service.dto.SiteCommand;
import kr.proten.pms.maintenance.service.entity.SiteChannel;

/**
 * 사이트 등록·수정 요청 (AC D2-4) — 계약 요청과 같은 이유로 한 record다.
 *
 * {@code contacts}에 {@code @Valid}가 붙어 있어야 목록 <b>안</b>의 제약까지 검사된다 —
 * 없으면 요소의 애너테이션이 조용히 무시되고 길이 초과가 DB까지 간다.
 *
 * @param engineerId 담당 엔지니어 — 미배정(null)이 정상 상태다
 * @param version    수정에서만 쓴다
 */
public record SiteRequest(
        @NotBlank(message = "사이트명은 필수입니다")
        @Size(max = 200, message = "사이트명은 200자를 넘을 수 없습니다") String name,
        SiteChannel channel,
        @Size(max = 300, message = "서버 사양은 300자를 넘을 수 없습니다") String serverSpec,
        Long engineerId,
        @Valid List<ContactRequest> contacts,
        Long version) {

    public SiteCommand toCommand() {
        List<ContactCommand> commands = contacts == null
                ? List.of()
                : contacts.stream().map(ContactRequest::toCommand).toList();

        return new SiteCommand(name, channel, serverSpec, engineerId, commands);
    }

    /** 수정에서만 필수다 (AC D2-4) — {@code ContractRequest}와 같은 이유. */
    public long requiredVersion() {
        if (version == null) {
            throw new ValidationException("version은 필수입니다", "version");
        }

        return version;
    }
}
