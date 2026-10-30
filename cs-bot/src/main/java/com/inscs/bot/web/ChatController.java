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
import java.util.UUID;

/**
 * 对话入口。整个 HTTP 层只有一个写接口。
 *
 * <p>没有 SSE 版本是<b>故意的</b>：P0 先证明「一轮的语义是对的」，
 * 流式只是把同一份 {@code TurnResult} 切成块发出去，属于传输层的事。
 * 等 blocks 的形状稳定了再上 SSE，否则前端会跟着一起返工。
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
        String traceId = newTraceId();
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? "c-" + traceId
                : request.conversationId();

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

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
