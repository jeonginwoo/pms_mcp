package kr.proten.pmshost.chat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 토큰 페이로드에서 클레임을 서명 검증 없이 훔쳐본다 — 날짜·identity 주입용
 * "힌트"일 뿐이다(구현_노트 §3-3). 권한 판정 근거가 아니며, 검증은 토큰을
 * 실제로 받는 MCP 서버가 한다(원칙 3·4).
 */
final class TokenHints {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Optional<String> claim(String token, String name) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return Optional.empty();
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = MAPPER.readTree(new String(payload, StandardCharsets.UTF_8)).get(name);

            return node == null || node.isNull() ? Optional.empty() : Optional.of(node.asText());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private TokenHints() {
    }

}
