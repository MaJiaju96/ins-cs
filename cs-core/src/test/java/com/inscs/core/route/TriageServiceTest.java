package com.inscs.core.route;

import com.inscs.core.ir.Domain;
import com.inscs.core.ir.RouteDecision;
import com.inscs.core.ir.TurnContext;
import com.inscs.core.port.RouterPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TriageServiceTest {

    private static TurnContext ctx(String input) {
        return TurnContext.newTurn("t-1", "c-1", 1L, "web", "u-1", input);
    }

    private static RouteRule surrenderRule() {
        return RouteRule.keyword("r-surrender", 10, "退保", Domain.BROKER, "policy.surrender", "broker.error");
    }

    @Test
    @DisplayName("规则命中时一次模型都不调——2 Mbps 链路上这是第一等公民")
    void ruleHitDoesNotTouchModel() {
        AtomicInteger calls = new AtomicInteger();
        RouterPort router = c -> {
            calls.incrementAndGet();
            return RouteDecision.hitModel(Domain.MERCHANT, "merchant.x", 0.99);
        };

        RouteDecision d = new TriageService(new SimpleRouteMatcher(), router)
                .decide(ctx("我想退保"), List.of(surrenderRule()));

        assertThat(d.state()).isEqualTo(RouteDecision.State.HIT_RULE);
        assertThat(d.domain()).isEqualTo(Domain.BROKER);
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("模型置信度不足时改反问，而不是硬分到一个域")
    void lowConfidenceFallsBackToAskBack() {
        RouterPort router = c -> RouteDecision.hitModel(Domain.MERCHANT, "merchant.refund", 0.41);

        RouteDecision d = new TriageService(new SimpleRouteMatcher(), router)
                .decide(ctx("那个退款的事"), List.of());

        assertThat(d.state()).isEqualTo(RouteDecision.State.ASK_BACK);
        assertThat(d.needsClarification()).isTrue();
    }

    @Test
    @DisplayName("模型挂了不能让请求失败，退化成反问")
    void modelFailureDegradesToAskBack() {
        RouterPort router = c -> {
            throw new IllegalStateException("upstream 502");
        };

        RouteDecision d = new TriageService(new SimpleRouteMatcher(), router)
                .decide(ctx("你好"), List.of());

        assertThat(d.state()).isEqualTo(RouteDecision.State.ASK_BACK);
    }

    @Test
    @DisplayName("模型返回 null 也不能 NPE")
    void nullFromModelIsTolerated() {
        RouteDecision d = new TriageService(new SimpleRouteMatcher(), c -> null)
                .decide(ctx("你好"), List.of());

        assertThat(d.state()).isEqualTo(RouteDecision.State.ASK_BACK);
    }
}
