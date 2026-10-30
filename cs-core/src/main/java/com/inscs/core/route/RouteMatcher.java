package com.inscs.core.route;

import com.inscs.core.ir.RouteDecision;
import com.inscs.core.ir.TurnContext;

import java.util.List;
import java.util.Optional;

/**
 * 规则匹配器。
 *
 * <p>只做一件事：给定输入和规则集，返回第一个命中。故意不提供「组合条件」「表达式引擎」——
 * 参考系统用了 MVEL + JsonPath + JSONata 三套表达式来支持不重启改 Agent 树，
 * 那是为 7 种目标 Agent 和多人协作准备的；一个人维护三套表达式语法是纯负债。
 *
 * <p>需要复杂条件时，写一个实现类，而不是加一套 DSL。
 */
@FunctionalInterface
public interface RouteMatcher {

    Optional<RouteDecision> match(TurnContext ctx, List<RouteRule> rules);
}
