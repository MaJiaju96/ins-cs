package com.inscs.core.route;

import com.inscs.core.ir.RouteDecision;
import com.inscs.core.ir.TurnContext;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 表驱动的简单规则匹配。关键字 / 正则 / 精确意图三种。
 *
 * <p>匹配顺序由 {@link RouteRule#priority()} 决定，数字小的先匹配。
 * 命中第一条即返回——这意味着规则集必须是<b>有序且无歧义</b>的，
 * 歧义靠 priority 显式表达，而不是靠插入顺序。
 */
public final class SimpleRouteMatcher implements RouteMatcher {

    @Override
    public Optional<RouteDecision> match(TurnContext ctx, List<RouteRule> rules) {
        if (ctx == null || rules == null || rules.isEmpty()) {
            return Optional.empty();
        }
        String input = ctx.userInput() == null ? "" : ctx.userInput().trim();
        if (input.isEmpty()) {
            return Optional.empty();
        }
        return rules.stream()
                .filter(RouteRule::enabled)
                .sorted(Comparator.comparingInt(RouteRule::priority))
                .filter(rule -> hits(rule, input))
                .findFirst()
                .map(rule -> RouteDecision.hitRule(
                        rule.target(), rule.intent(), rule.id(), rule.fallbackMsgKey()));
    }

    private static boolean hits(RouteRule rule, String input) {
        return switch (rule.matchKind()) {
            case KEYWORD -> input.contains(rule.pattern());
            case EXACT -> input.trim().toLowerCase(Locale.ROOT)
                    .equals(rule.pattern().trim().toLowerCase(Locale.ROOT));
            case REGEX -> java.util.regex.Pattern.compile(rule.pattern())
                    .matcher(input)
                    .find();
        };
    }
}
