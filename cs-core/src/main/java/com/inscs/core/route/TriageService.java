package com.inscs.core.route;

import com.inscs.core.ir.Domain;
import com.inscs.core.ir.RouteDecision;
import com.inscs.core.ir.TurnContext;
import com.inscs.core.port.RouterPort;

import java.util.List;
import java.util.Optional;

/**
 * 分流编排：<b>规则优先，小模型兜底，都没有就反问</b>。
 *
 * <p>三条出口都是正常路径：
 * <ol>
 *   <li>规则命中 → 零模型调用、零额外往返；</li>
 *   <li>模型命中且置信度够 → 一次往返；</li>
 *   <li>模型不可用 / 置信度不足 → {@link RouteDecision#askBack}，一次低成本反问。</li>
 * </ol>
 *
 * <p><b>模型不可用绝不能让请求失败。</b>这是分流层最重要的一条：
 * 分流是「快路径」，它挂了的正确表现是退化到反问，而不是 500。
 */
public final class TriageService {

    private final RouteMatcher matcher;
    private final RouterPort routerPort;

    public TriageService(RouteMatcher matcher, RouterPort routerPort) {
        this.matcher = matcher;
        this.routerPort = routerPort;
    }

    /**
     * 判定分流。
     *
     * @param rules 规则集；可以为空（例如 P0 阶段只写死几条）
     */
    public RouteDecision decide(TurnContext ctx, List<RouteRule> rules) {
        Optional<RouteDecision> byRule = matcher.match(ctx, rules);
        if (byRule.isPresent()) {
            return byRule.get();
        }

        RouteDecision byModel = null;
        try {
            byModel = routerPort.classify(ctx);
        } catch (RuntimeException e) {
            // 模型超时/限流/解析失败：静默降级到反问。
            // 注意不要在这里打个 error 就完事——调用方会把 state=ASK_BACK 记进 turn 表，
            // 事后能统计出「反问率异常升高」= 分流模型在退化的信号。
        }

        if (byModel != null
                && byModel.state() == RouteDecision.State.HIT_MODEL
                && byModel.confidence() >= RouteDecision.MIN_MODEL_CONFIDENCE) {
            return byModel;
        }
        return RouteDecision.askBack(Domain.ENTRY);
    }
}
