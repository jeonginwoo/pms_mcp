package kr.proten.pmshost.chat;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * B2-3 관통 테스트용 최소 챗 진입점. 실전에서 챗 위젯은 항상 PMS chat BFF를
 * 거치고(구현_노트 §1-2 — 레이트리밋·입력 차단·위임 토큰 발급은 BFF 소유),
 * 이 엔드포인트는 BFF가 서있을 자리를 대신한다. 사용자 토큰은 저장하지 않고
 * 요청마다 그대로 /mcp에 싣는다(원칙 4).
 */
@RestController
public class ChatController {

    record ChatRequest(String conversationId, String message) {
    }

    record ChatResponse(String conversationId, String reply) {
    }

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    ChatResponse chat(@RequestBody ChatRequest req,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization: Bearer <토큰>이 필요합니다");
        }

        String token = authorization.substring("Bearer ".length());
        if (TokenHints.claim(token, "sub").isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰에서 사용자(sub)를 읽을 수 없습니다");
        }

        if (req == null || req.message() == null || req.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 비울 수 없습니다");
        }

        String conversationId = req.conversationId() == null || req.conversationId().isBlank()
                ? "default" : req.conversationId();
        String reply = chatService.chat(conversationId, req.message(), token);

        return new ChatResponse(conversationId, reply);
    }

}
