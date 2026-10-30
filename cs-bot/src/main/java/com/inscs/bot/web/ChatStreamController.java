package com.inscs.bot.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inscs.core.ConversationService;
import com.inscs.core.ir.Block;
import com.inscs.core.ir.RouteDecision;
import com.inscs.core.ir.TurnContext;
import com.inscs.core.ir.TurnResult;
import com.inscs.core.port.TurnObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 流式对话：把一轮的执行过程按事件推出去。
 *
 * <p>事件只用四个名字，刻意少：
 * <pre>
 * event: route   {domain,intent,state,confidence,ruleId}   分流定了就发，比任何 block 都早
 * event: block   {kind,text,cardType,data,...}             一个 block 一帧，顺序即渲染顺序
 * event: done    {traceId,seq,usage,spans}                 完整 trace 与用量，落库/评测都用它
 * event: error   {message}                                  链路异常（不是业务降级）
 * </pre>
 *
 * <p><b>业务降级不走 error 事件</b>：路由没命中、Agent 抛错，内核都会转成
 * {@code state=DEGRADED} + 一句能听懂的话，那是正常的 {@code block}/{@code done}。
 * {@code error} 只留给「这一轮根本没跑起来」。
 *
 * <p>为什么不用前端的 {@code EventSource}：它只能 GET，而对话要 POST 消息体。
 * 所以前端用 fetch + ReadableStream 自己解 SSE 帧——协议一样，只是换个读法。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatStreamController {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamController.class);

    /** 单轮 30s：够慢模型跑完，又不至于让一个卡死的连接挂一小时。 */
    private static final long TIMEOUT_MS = 30_000L;

    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final ExecutorService turnExecutor;

    public ChatStreamController(ConversationService conversationService,
                                ObjectMapper objectMapper,
                                ExecutorService turnExecutor) {
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
        this.turnExecutor = turnExecutor;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        String traceId = Ids.newTraceId();
        String conversationId = Ids.conversationId(request.conversationId(), traceId);

        TurnContext ctx = TurnContext.newTurn(
                traceId,
                conversationId,
                request.seqOrDefault(),
                request.channelOrDefault(),
                request.userIdOrDefault(),
                request.message());

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        SseSession session = new SseSession(emitter);

        // 客户端断开 / 超时：不抛、不重试，安静收场（人已经走了，再推也没人收）
        emitter.onTimeout(session::closeQuietly);
        emitter.onError(e -> session.markBroken());

        turnExecutor.execute(() -> {
            MDC.put("traceId", traceId);
            try {
                // 与同步接口走同一段编排，没有第二条业务链路
                conversationService.handle(ctx, session);
            } catch (RuntimeException e) {
                log.warn("turn failed, traceId={}", traceId, e);
                session.sendError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                session.closeQuietly();
                MDC.remove("traceId");
            }
        });

        return emitter;
    }

    /** 把 observer 回调翻译成 SSE 帧。所有 IO 异常都在这里被吃掉，绝不冒泡回内核。 */
    private final class SseSession implements TurnObserver {

        private final SseEmitter emitter;
        private volatile boolean broken;

        private SseSession(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onRoute(TurnContext ctx, RouteDecision route) {
            send("route", new ChatResponse.RouteView(route.domain().code(), route.intent(),
                    route.state().name(), route.confidence(), route.ruleId()));
        }

        @Override
        public void onBlock(Block block) {
            send("block", BlockView.of(block));
        }

        @Override
        public void onComplete(TurnResult result) {
            ChatResponse view = ChatResponse.of(result);
            send("done", Map.of(
                    "traceId", view.traceId(),
                    "seq", view.seq(),
                    "usage", view.usage(),
                    "spans", view.spans()));
        }

        void sendError(String message) {
            send("error", Map.of("message", message));
        }

        /** 客户端断了以后不再尝试写入——这就是「旁路」的定义。 */
        private void send(String event, Object payload) {
            if (broken) {
                return;
            }
            try {
                emitter.send(SseEmitter.event()
                        .name(event)
                        .data(objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                markBroken();
            }
        }

        void markBroken() {
            broken = true;
        }

        void closeQuietly() {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // 已经完成或已断开
            }
        }
    }
}
