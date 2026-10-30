package com.inscs.core.agent;

import com.inscs.core.ir.Domain;
import com.inscs.core.ir.TurnContext;
import com.inscs.core.ir.TurnResult;

/**
 * 子域 Agent 的扩展点（插件）。
 *
 * <p>新增一个业务域 = 实现本接口 + 注册成 Bean + 在 {@code route_rule} 里加一行。
 * 不需要改分流器、不需要改入口、不需要改 trace。
 *
 * <p>三条纪律：
 * <ol>
 *   <li>实现类<b>不许</b>回查数据库——需要的业务快照应该在入口层一次查全，放在
 *       {@link TurnContext#bizContext()} 里下发；</li>
 *   <li>失败时抛异常或返回 {@link com.inscs.core.ir.RouteDecision.State#DEGRADED} 语义的结果，
 *       由上层决定用哪条降级话术，不要自己拼「系统繁忙」；</li>
 *   <li>每一级降级都要留下一个 span（状态 DEGRADED），否则降级链会悄悄腐烂。</li>
 * </ol>
 */
public interface SubAgent {

    /** 本 Agent 负责的域。注册表按它索引，必须唯一。 */
    Domain domain();

    /**
     * 是否接管本轮。
     *
     * <p>默认认为「路由已经判到我的域，就该我接」。需要更细粒度判断（例如按意图白名单）时才覆盖。
     */
    default boolean canHandle(TurnContext ctx) {
        return true;
    }

    /**
     * 处理一轮。
     *
     * <p>入参的 {@link TurnContext#route()} 一定非空——分流已经做完。
     */
    TurnResult handle(TurnContext ctx);
}
