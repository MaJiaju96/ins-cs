package com.inscs.core;

import com.inscs.core.agent.SubAgent;
import com.inscs.core.agent.SubAgentRegistry;
import com.inscs.core.ir.Block;
import com.inscs.core.ir.Domain;
import com.inscs.core.ir.RunTrace;
import com.inscs.core.ir.Span;
import com.inscs.core.ir.TurnContext;
import com.inscs.core.ir.TurnResult;
import com.inscs.core.port.EventPublisher;
import com.inscs.core.route.RouteRule;
import com.inscs.core.route.SimpleRouteMatcher;
import com.inscs.core.route.TriageService;
import com.inscs.core.text.FallbackMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationServiceTest {

    private static TurnContext ctx(String input) {
        return TurnContext.newTurn("t-1", "c-1", 3L, "web", "u-1", input);
    }

    private static RouteRule surrenderRule() {
        return RouteRule.keyword("r-surrender", 10, "退保", Domain.BROKER, "policy.surrender", "broker.error");
    }

    private record Rig(ConversationService service, List<TurnResult> sunk, List<String> topics) {
    }

    private static Rig rig(SubAgent agent) {
        SubAgentRegistry registry = new SubAgentRegistry();
        if (agent != null) {
            registry.register(agent);
        }
        TriageService triage = new TriageService(new SimpleRouteMatcher(),
                c -> com.inscs.core.ir.RouteDecision.askBack(Domain.ENTRY));
        List<TurnResult> sunk = new ArrayList<>();
        List<String> topics = new ArrayList<>();
        ConversationService svc = new ConversationService(
                triage,
                () -> List.of(surrenderRule()),
                registry,
                sunk::add,
                (topic, payload) -> topics.add(topic));
        return new Rig(svc, sunk, topics);
    }

    private static SubAgent brokerAgent(java.util.function.Function<TurnContext, TurnResult> body) {
        return new SubAgent() {
            @Override
            public Domain domain() {
                return Domain.BROKER;
            }

            @Override
            public TurnResult handle(TurnContext c) {
                return body.apply(c);
            }
        };
    }

    @Test
    @DisplayName("规则命中 → 进域 Agent → trace 里有 route span + agent span，且事件发出")
    void happyPathMergesTrace() {
        SubAgent agent = brokerAgent(c -> new TurnResult(
                c.traceId(), c.conversationId(), c.seq(), c.route(),
                List.of(new Block.Text("退保损失大约 3%，确认要办吗")),
                new TurnResult.Usage(120, 40),
                new RunTrace(c.traceId(), List.of(new Span("s9", null, Span.Type.MODEL, "llm.broker",
                        0L, 1L, null, Span.Status.OK, java.util.Map.of(), null)))));

        Rig rig = rig(agent);
        TurnResult r = rig.service().handle(ctx("我想退保"));

        assertThat(r.route().state()).isEqualTo(com.inscs.core.ir.RouteDecision.State.HIT_RULE);
        assertThat(r.plainText()).contains("3%");
        assertThat(r.usage().total()).isEqualTo(160);
        assertThat(r.trace().byType(Span.Type.ROUTE)).hasSize(1);
        assertThat(r.trace().byType(Span.Type.MODEL)).hasSize(1);

        assertThat(rig.topics()).contains(EventPublisher.Topics.TURN_COMPLETED);
        assertThat(rig.sunk()).hasSize(1);
    }

    @Test
    @DisplayName("路由判到没有实现的域 → 优雅降级 + route.miss 事件，不是异常")
    void routeMissDegradesGracefully() {
        Rig rig = rig(null);
        TurnResult r = rig.service().handle(ctx("我想退保"));

        assertThat(r.route().state()).isEqualTo(com.inscs.core.ir.RouteDecision.State.DEGRADED);
        assertThat(r.plainText()).isEqualTo(FallbackMessages.ROUTE_MISS);
        assertThat(rig.topics()).contains(EventPublisher.Topics.ROUTE_MISS);
        assertThat(rig.sunk()).hasSize(1);
    }

    @Test
    @DisplayName("域 Agent 抛异常 → 用该域自己的降级话术，trace 记 DEGRADED/ERROR")
    void agentFailureUsesDomainFallback() {
        Rig rig = rig(brokerAgent(c -> {
            throw new IllegalStateException("order rpc timeout");
        }));

        TurnResult r = rig.service().handle(ctx("我要退保"));

        assertThat(r.route().state()).isEqualTo(com.inscs.core.ir.RouteDecision.State.DEGRADED);
        assertThat(r.plainText()).isEqualTo(FallbackMessages.of("broker.error"));
        assertThat(r.trace().hasError()).isTrue();
        assertThat(rig.topics()).contains(EventPublisher.Topics.TOOL_FAILED);
    }

    @Test
    @DisplayName("trace 落库失败不能拖死主链路")
    void sinkFailureDoesNotBreakTurn() {
        SubAgentRegistry registry = new SubAgentRegistry().register(brokerAgent(c -> new TurnResult(
                c.traceId(), c.conversationId(), c.seq(), c.route(),
                List.of(new Block.Text("ok")), TurnResult.Usage.ZERO, new RunTrace(c.traceId(), List.of()))));
        ConversationService svc = new ConversationService(
                new TriageService(new SimpleRouteMatcher(), c -> com.inscs.core.ir.RouteDecision.askBack(Domain.ENTRY)),
                () -> List.of(surrenderRule()),
                registry,
                r -> {
                    throw new IllegalStateException("db down");
                },
                (t, p) -> {
                    throw new IllegalStateException("mq down");
                });

        TurnResult r = svc.handle(ctx("我想退保"));

        assertThat(r.plainText()).isEqualTo("ok");
    }
}
