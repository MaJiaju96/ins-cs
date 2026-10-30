package com.inscs.core.route;

import com.inscs.core.ir.Domain;
import com.inscs.core.ir.RouteDecision;
import com.inscs.core.ir.TurnContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleRouteMatcherTest {

    private final SimpleRouteMatcher matcher = new SimpleRouteMatcher();

    private static TurnContext ctx(String input) {
        return TurnContext.newTurn("t-1", "c-1", 1L, "web", "u-1", input);
    }

    @Test
    @DisplayName("priority 小的先命中，而不是列表顺序")
    void priorityWins() {
        RouteRule coarse = new RouteRule("r-coarse", 20, RouteRule.MatchKind.KEYWORD, "保险",
                Domain.BROKER, "insurance.general", 1.0, null, true);
        RouteRule fine = new RouteRule("r-fine", 10, RouteRule.MatchKind.KEYWORD, "退保",
                Domain.BROKER, "policy.surrender", 1.0, "broker.error", true);

        Optional<RouteDecision> hit = matcher.match(ctx("我要退保"), List.of(coarse, fine));

        assertThat(hit).isPresent();
        assertThat(hit.get().intent()).isEqualTo("policy.surrender");
        assertThat(hit.get().ruleId()).isEqualTo("r-fine");
        assertThat(hit.get().state()).isEqualTo(RouteDecision.State.HIT_RULE);
        assertThat(hit.get().usedModel()).isFalse();
    }

    @Test
    @DisplayName("disabled 规则不参与匹配")
    void disabledRuleSkipped() {
        RouteRule off = new RouteRule("r-off", 1, RouteRule.MatchKind.KEYWORD, "退保",
                Domain.BROKER, "policy.surrender", 1.0, null, false);

        assertThat(matcher.match(ctx("我要退保"), List.of(off))).isEmpty();
    }

    @Test
    @DisplayName("正则与精确匹配")
    void regexAndExact() {
        RouteRule orderNo = RouteRule.regex("r-order", 5, "[A-Z]\\d{4,}", Domain.BROKER, "order.query", null);
        RouteRule menu2 = new RouteRule("r-menu-2", 5, RouteRule.MatchKind.EXACT, "2",
                Domain.BROKER, "policy.surrender", 1.0, null, true);

        assertThat(matcher.match(ctx("订单 A1024 想退保"), List.of(orderNo))).isPresent();
        assertThat(matcher.match(ctx(" 2 "), List.of(menu2))).isPresent();
        assertThat(matcher.match(ctx("20"), List.of(menu2))).isEmpty();
    }

    @Test
    @DisplayName("空输入不命中任何规则")
    void blankInputMatchesNothing() {
        RouteRule r = RouteRule.keyword("r1", 1, "退保", Domain.BROKER, "policy.surrender", null);
        assertThat(matcher.match(ctx("   "), List.of(r))).isEmpty();
    }

    @Test
    @DisplayName("非法正则规则在构造期就炸，而不是等到线上第一次命中")
    void badRegexFailsFast() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> RouteRule.regex("r-bad", 1, "([", Domain.BROKER, "x", null))
                .isInstanceOf(java.util.regex.PatternSyntaxException.class);
    }
}
