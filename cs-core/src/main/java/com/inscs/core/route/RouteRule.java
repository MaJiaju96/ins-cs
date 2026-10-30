package com.inscs.core.route;

import com.inscs.core.ir.Domain;

import java.util.regex.Pattern;

/**
 * 一条分流规则。对应 {@code route_rule} 表的一行。
 *
 * <p><b>事实源在仓库里</b>（Flyway 迁移 + 代码 review），不在配置中心。
 * 理由来自参考系统的真实代价：当 Agent 树/路由全在动态配置里时，
 * 「线上为什么这么分流」在仓库里查不到，也测不了——三个月后没人说得清。
 *
 * <p>所以我们的线划在：<b>结构与规则进仓库，参数与开关进配置中心</b>。
 *
 * @param id             规则 id，写进 trace，可反查
 * @param priority       数字小的先匹配；越小越具体
 * @param matchKind      匹配方式
 * @param pattern        关键字 / 正则 / 精确意图
 * @param target         命中后分流到的域
 * @param intent         命中后的意图标识
 * @param confidence     本规则的置信度，通常 1.0（规则命中不猜）
 * @param fallbackMsgKey 该域失败时的降级话术键；每个域都该有，而不是全局一句「系统繁忙」
 * @param enabled        开关。下线一条规则优先改这个，而不是删数据
 */
public record RouteRule(
        String id,
        int priority,
        MatchKind matchKind,
        String pattern,
        Domain target,
        String intent,
        double confidence,
        String fallbackMsgKey,
        boolean enabled
) {

    public enum MatchKind {
        /** 包含关键字。 */
        KEYWORD,
        /** 正则匹配。 */
        REGEX,
        /** 用户输入恰好等于该值（忽略大小写与空白）。 */
        EXACT
    }

    public RouteRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("rule id must not be blank");
        }
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern must not be blank: " + id);
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null: " + id);
        }
        if (matchKind == MatchKind.REGEX) {
            // 提前编译一次，让错误的规则在装配期就炸，而不是在线上第一次命中时炸
            Pattern.compile(pattern);
        }
    }

    public static RouteRule keyword(String id, int priority, String keyword, Domain target, String intent,
                                    String fallbackMsgKey) {
        return new RouteRule(id, priority, MatchKind.KEYWORD, keyword, target, intent, 1.0, fallbackMsgKey, true);
    }

    public static RouteRule regex(String id, int priority, String regex, Domain target, String intent,
                                  String fallbackMsgKey) {
        return new RouteRule(id, priority, MatchKind.REGEX, regex, target, intent, 1.0, fallbackMsgKey, true);
    }

    @Override
    public String toString() {
        return "RouteRule[" + id + " p" + priority + " " + matchKind + ":" + pattern
                + " -> " + target.code() + "/" + intent + (enabled ? "" : " (disabled)") + "]";
    }
}
