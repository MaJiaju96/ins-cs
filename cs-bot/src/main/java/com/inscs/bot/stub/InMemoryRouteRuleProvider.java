package com.inscs.bot.stub;

import com.inscs.core.ir.Domain;
import com.inscs.core.route.RouteRule;

import java.util.List;

/**
 * P0 规则集：写死在代码里，够跑通样板房。
 *
 * <p>刻意不做成 yaml 外置——先让「规则是代码、可 diff、可单测」这件事成立，
 * P1 换 MariaDB 时只是把 load() 的实现换掉，规则对象本身不变。
 */
public final class InMemoryRouteRuleProvider implements com.inscs.core.port.RouteRuleProvider {

    private static final List<RouteRule> RULES = List.of(
            // 数字菜单（反问后用户回数字，走确定性分支，不再过模型）
            new RouteRule("menu.order", 10, RouteRule.MatchKind.EXACT, "1",
                    Domain.BROKER, "order.query", 1.0, "broker.error", true),
            new RouteRule("menu.surrender", 10, RouteRule.MatchKind.EXACT, "2",
                    Domain.BROKER, "policy.surrender", 1.0, "broker.error", true),
            new RouteRule("menu.claim", 10, RouteRule.MatchKind.EXACT, "3",
                    Domain.BROKER, "claim.materials", 1.0, "broker.error", true),

            // 订单号：A + 4 位以上数字
            RouteRule.regex("order.no", 20, "[Aa]\\d{4,}", Domain.BROKER, "order.query", "broker.error"),

            // 退保/退钱/退款
            new RouteRule("kw.surrender", 30, RouteRule.MatchKind.KEYWORD, "退保",
                    Domain.BROKER, "policy.surrender", 1.0, "broker.error", true),
            new RouteRule("kw.refund", 31, RouteRule.MatchKind.KEYWORD, "退钱",
                    Domain.BROKER, "policy.surrender", 1.0, "broker.error", true),

            // 理赔材料
            new RouteRule("kw.claim", 40, RouteRule.MatchKind.KEYWORD, "理赔",
                    Domain.BROKER, "claim.materials", 1.0, "broker.error", true),
            new RouteRule("kw.material", 41, RouteRule.MatchKind.KEYWORD, "材料",
                    Domain.BROKER, "claim.materials", 1.0, "broker.error", true),

            // 查订单
            new RouteRule("kw.order", 50, RouteRule.MatchKind.KEYWORD, "订单",
                    Domain.BROKER, "order.query", 1.0, "broker.error", true),

            // 转人工：直接命中 handoff
            new RouteRule("kw.human", 5, RouteRule.MatchKind.KEYWORD, "转人工",
                    Domain.BROKER, "handoff", 1.0, "broker.error", true)
    );

    @Override
    public java.util.List<RouteRule> load() {
        return RULES;
    }
}
