package com.inscs.core.port;

import com.inscs.core.route.RouteRule;

import java.util.List;

/**
 * 规则加载端口。
 *
 * <p>骨架阶段由实现类返回写死的几条；P1 接 MariaDB 的 {@code route_rule} 表。
 * 无论数据从哪来，<b>规则必须是可 diff、可 review 的</b>——这是刻意与参考系统相反的选择。
 *
 * <p>实现要求：带缓存 + 定时刷新（例如 60s），不要每轮请求查库；
 * 刷新失败保留旧值，不能让一次数据库抖动把分流打成全反问。
 */
public interface RouteRuleProvider {

    List<RouteRule> load();
}
