package com.inscs.bot.web;

import com.inscs.core.ConversationService;
import com.inscs.core.ir.TurnContext;
import com.inscs.core.ir.TurnResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 同步对话入口。流式在 {@link ChatStreamController}，两者走同一段编排。
 *
 * <p>同步接口留着不是冗余：curl、评测台批量跑、断点调试都更适合「一次拿完整结果」。
 * 流式只是同一份 {@code TurnResult} 换个发法。
 */
@RestController
@RequestMapping(value = "/api/chat", produces = MediaType.APPLICATION_JSON_VALUE)
public class ChatController {

    private final ConversationService conversationService;

    public ChatController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String traceId = Ids.newTraceId();
        String conversationId = Ids.conversationId(request.conversationId(), traceId);

        TurnContext ctx = TurnContext.newTurn(
                traceId,
                conversationId,
                request.seqOrDefault(),
                request.channelOrDefault(),
                request.userIdOrDefault(),
                request.message());

        TurnResult result = conversationService.handle(ctx);
        return ChatResponse.of(result);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "cs-bot");
    }
}
