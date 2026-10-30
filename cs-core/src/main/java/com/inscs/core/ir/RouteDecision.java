package com.inscs.core.ir;

/**
 * 一次分流决策。
 *
 * <p><b>核心设计：不确定性是合法状态，不是异常。</b>四种状态都是正常路径，只有 IO/参数错误才算异常。
 * 这带来两个直接收益：
 * <ol>
 *   <li>{@link State#HIT_RULE} 不需要模型，少一次往返（2 Mbps 链路上这是第一等公民）；</li>
 *   <li>{@link State#ASK_BACK} 让「我不知道」变成一次低成本反问，而不是一次幻觉。</li>
 * </ol>
 *
 * <p>这个对象必须落进 {@code turn} 表：它支撑「路由命中率」与「误问率」两条硬指标。
 * 不落库，这一整块能力就不可测。
 *
 * @param domain        分流到的业务域
 * @param intent        意图标识（规则命中时取规则声明的 intent；模型命中时取模型输出）
 * @param state         决策来源与状态
 * @param confidence    置信度，规则命中固定 1.0，模型命中取模型自评
 * @param ruleId        命中的规则 id，模型/反问时为 null
 * @param fallbackMsgKey 该域失败时使用的降级话术键（见 {@code route_rule.fallback_msg_key}）
 */
public record RouteDecision(
        Domain domain,
        String intent,
        State state,
        double confidence,
        String ruleId,
        String fallbackMsgKey
) {

    /** 决策状态。顺序即优先级。 */
    public enum State {
        /** 规则命中，未调用模型。 */
        HIT_RULE,
        /** 小模型兜底命中。 */
        HIT_MODEL,
        /** 无法确定，向用户反问。 */
        ASK_BACK,
        /** 命中但该域不可用（未实现/被熔断），走降级话术。 */
        DEGRADED
    }

    /** 模型兜底的最低可接受置信度，低于此值一律反问，不猜。 */
    public static final double MIN_MODEL_CONFIDENCE = 0.6;

    public RouteDecision {
        if (domain == null) {
            throw new IllegalArgumentException("domain must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
    }

    public static RouteDecision hitRule(Domain domain, String intent, String ruleId, String fallbackMsgKey) {
        return new RouteDecision(domain, intent, State.HIT_RULE, 1.0, ruleId, fallbackMsgKey);
    }

    public static RouteDecision hitModel(Domain domain, String intent, double confidence) {
        return new RouteDecision(domain, intent, State.HIT_MODEL, confidence, null, null);
    }

    public static RouteDecision askBack(Domain domain) {
        return new RouteDecision(domain, null, State.ASK_BACK, 0.0, null, null);
    }

    public static RouteDecision degraded(Domain domain, String intent, String fallbackMsgKey) {
        return new RouteDecision(domain, intent, State.DEGRADED, 1.0, null, fallbackMsgKey);
    }

    /** 是否消耗过一次模型调用。评测里用来区分「规则救回来的」与「模型猜出来的」。 */
    public boolean usedModel() {
        return state == State.HIT_MODEL;
    }

    /** 是否需要向用户反问。 */
    public boolean needsClarification() {
        return state == State.ASK_BACK;
    }
}
