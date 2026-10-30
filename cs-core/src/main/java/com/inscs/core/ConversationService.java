package com.inscs.core;

import com.inscs.core.agent.SubAgent;
import com.inscs.core.agent.SubAgentRegistry;
import com.inscs.core.ir.Block;
import com.inscs.core.ir.RouteDecision;
import com.inscs.core.ir.RunTrace;
import com.inscs.core.ir.Span;
import com.inscs.core.ir.TurnContext;
import com.inscs.core.ir.TurnResult;
import com.inscs.core.port.EventPublisher;
import com.inscs.core.port.RouteRuleProvider;
import com.inscs.core.port.TraceSink;
import com.inscs.core.port.TurnObserver;
import com.inscs.core.route.TriageService;
import com.inscs.core.text.FallbackMessages;
import com.inscs.core.trace.TraceRecorder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 一轮对话的编排：分流 → 找 Agent → 执行 → 补 trace → 旁路落库。
 *
 * <p>这是整个内核唯一「知道全局」的类，故意只有几十行。
 * 它回答一个问题：<b>一轮请求最多经过几次往返？</b>
 * 答案应该是：规则命中 0 次模型调用；模型兜底 1 次；Agent 内部自管。
 * 入口层只在需要时查一次业务快照（订单/保单），下游域 Agent 不许回查。
 *
 * <p>失败策略统一在这里：任何一层抛异常，都转成「带降级话术的 {@link RouteDecision.State#DEGRADED} 结果」，
 * 而不是 500。用户永远能拿到一句能听懂的话 + 一个出路。
 */
public final class ConversationService {

    private final TriageService triage;
    private final RouteRuleProvider ruleProvider;
    private final SubAgentRegistry registry;
    private final TraceSink traceSink;
    private final EventPublisher events;

    public ConversationService(TriageService triage,
                               RouteRuleProvider ruleProvider,
                               SubAgentRegistry registry,
                               TraceSink traceSink,
                               EventPublisher events) {
        this.triage = triage;
        this.ruleProvider = ruleProvider;
        this.registry = registry;
        this.traceSink = traceSink;
        this.events = events;
    }

    public TurnResult handle(TurnContext ctx) {
        return handle(ctx, TurnObserver.NOOP);
    }

    /**
     * 带观察者的版本：同步 JSON 接口用上面那个（NOOP），SSE 接口用这个。
     * 两条路径走<b>同一段编排</b>——这是有意的，流式不能是第二条业务链路。
     */
    public TurnResult handle(TurnContext ctx, TurnObserver observer) {
        TraceRecorder recorder = new TraceRecorder(ctx.traceId());

        // 1) 分流
        long t0 = System.currentTimeMillis();
        RouteDecision route = triage.decide(ctx, ruleProvider.load());
        Span routeSpan = recorder.addCompleted(null, Span.Type.ROUTE, "triage",
                System.currentTimeMillis() - t0, Span.Status.OK, null);
        notifyRoute(observer, ctx, route);

        // 反问态：不进入任何域 Agent，直接返回澄清。
        // 注意反问也带编号候选（1/2/3），用户回数字走规则确定性分支，不再过模型。
        if (route.needsClarification()) {
            String msgKey = route.fallbackMsgKey() == null
                    ? FallbackMessages.ROUTE_CLARIFY
                    : route.fallbackMsgKey();
            TurnResult clarify = result(ctx, route,
                    List.of(new Block.Text(FallbackMessages.of(msgKey))), recorder.spans());
            safeSink(clarify);
            return finish(clarify, observer);
        }

        TurnContext routed = ctx.withRoute(route);

        // 2) 找 Agent。路由判到了域但没有实现（例如 P1 的延保）→ 优雅降级，不是异常
        Optional<SubAgent> agent = registry.resolve(routed);
        if (agent.isEmpty()) {
            RouteDecision degraded = RouteDecision.degraded(route.domain(),
                    route.intent(), FallbackMessages.ROUTE_MISS);
            TurnResult miss = result(routed, degraded,
                    List.of(new Block.Text(FallbackMessages.ROUTE_MISS)), recorder.spans());
            Span missSpan = recorder.degrade(routeSpan.spanId(), Span.Type.ROUTE, "route.miss",
                    "no agent for domain " + route.domain().code());
            miss = withSpans(miss, recorder.spans());
            safePublish(EventPublisher.Topics.ROUTE_MISS,
                    Map.of("traceId", ctx.traceId(), "seq", ctx.seq(),
                            "domain", route.domain().code(), "spanId", missSpan.spanId()));
            safeSink(miss);
            return finish(miss, observer);
        }

        // 3) 执行域 Agent
        TurnResult result;
        try {
            result = recorder.record(routeSpan.spanId(), Span.Type.TOOL, "agent." + agent.get().domain().code(),
                    () -> agent.get().handle(routed));
        } catch (RuntimeException e) {
            RouteDecision degraded = RouteDecision.degraded(route.domain(), route.intent(),
                    route.fallbackMsgKey() == null ? FallbackMessages.DEFAULT : route.fallbackMsgKey());
            result = result(routed, degraded,
                    List.of(new Block.Text(FallbackMessages.of(degraded.fallbackMsgKey()))), recorder.spans());
            safePublish(EventPublisher.Topics.TOOL_FAILED,
                    Map.of("traceId", ctx.traceId(), "seq", ctx.seq(),
                            "domain", route.domain().code(), "error", String.valueOf(e.getMessage())));
        }

        // 4) 合并 trace：路由 span + Agent 自己的 span，统一写进同一条事实
        List<Span> merged = new ArrayList<>();
        merged.add(routeSpan);
        if (result.trace() != null) {
            merged.addAll(result.trace().spans());
        }
        TurnResult final_ = withSpans(result, merged);

        safeSink(final_);
        safePublish(EventPublisher.Topics.TURN_COMPLETED, Map.of(
                "traceId", final_.traceId(),
                "seq", final_.seq(),
                "domain", final_.route().domain().code(),
                "state", final_.route().state().name(),
                "degraded", final_.trace().degradedCount() > 0));
        return finish(final_, observer);
    }

    /** 逐个 block 通知，最后给完整结果。观察者出错只记日志：用户的话不能因为订阅端挂了而消失。 */
    private static TurnResult finish(TurnResult result, TurnObserver observer) {
        for (Block block : result.blocks()) {
            try {
                observer.onBlock(block);
            } catch (RuntimeException ignored) {
                // 下发给订阅端失败（如 SSE 断开），不影响本轮结果
            }
        }
        try {
            observer.onComplete(result);
        } catch (RuntimeException ignored) {
            // 同上
        }
        return result;
    }

    private static void notifyRoute(TurnObserver observer, TurnContext ctx, RouteDecision route) {
        try {
            observer.onRoute(ctx, route);
        } catch (RuntimeException ignored) {
            // 分流已经算出来了，推送失败不该让整轮失败
        }
    }

    private static TurnResult result(TurnContext ctx, RouteDecision route, List<Block> blocks, List<Span> spans) {
        return new TurnResult(ctx.traceId(), ctx.conversationId(), ctx.seq(), route, blocks,
                TurnResult.Usage.ZERO, new RunTrace(ctx.traceId(), spans));
    }

    private static TurnResult withSpans(TurnResult r, List<Span> spans) {
        return new TurnResult(r.traceId(), r.conversationId(), r.seq(), r.route(), r.blocks(),
                r.usage(), new RunTrace(r.traceId(), spans));
    }

    /** trace 是旁路：失败了也不能影响用户已经拿到的话。 */
    private void safeSink(TurnResult r) {
        try {
            traceSink.accept(r);
        } catch (RuntimeException ignored) {
            // 结构化日志由实现方负责；这里只保证主链路不被拖死
        }
    }

    private void safePublish(String topic, Object payload) {
        try {
            events.publish(topic, payload);
        } catch (RuntimeException ignored) {
            // 同上：事件丢了，卫星少一条；主链路不能因此失败
        }
    }
}
