package com.inscs.core.retrieval;

/**
 * 检索置信度三段策略。
 *
 * <p><b>巧思来源：</b>参考系统把检索结果分成三段而不是两段——
 * {@code >=0.8} 直接答、{@code 0.3~0.8} 输出编号候选澄清问题、{@code <0.3} 跳过。
 * 关键不在阈值，而在<b>中间态的动作是「问」而不是「答」</b>：
 * 把不确定性转成一次低成本交互，而不是转成幻觉。
 *
 * <p>两个工程约束：
 * <ol>
 *   <li>阈值必须是<b>配置</b>，不是常量——否则评测台没法对「阈值是否合理」做回归；</li>
 *   <li>三段的返回类型必须是三种可区分的结果，不能压成 boolean。</li>
 * </ol>
 */
public record ConfidencePolicy(double high, double low) {

    public ConfidencePolicy {
        if (low <= 0 || high >= 1) {
            throw new IllegalArgumentException("thresholds must be within (0,1): low=" + low + " high=" + high);
        }
        if (low >= high) {
            throw new IllegalArgumentException("low must be < high: low=" + low + " high=" + high);
        }
    }

    /** 默认 0.8 / 0.3，与参考系统一致；可被配置覆盖。 */
    public static ConfidencePolicy defaults() {
        return new ConfidencePolicy(0.8, 0.3);
    }

    /** 检索结果的处置方式。 */
    public enum Outcome {
        /** 高置信：直接基于召回内容作答。 */
        ANSWER,
        /** 中间态：给出编号候选，反问一句。 */
        CLARIFY,
        /** 低置信：不猜，走兜底话术 + 转人工入口。 */
        REFUSE
    }

    public Outcome outcome(double topScore) {
        if (topScore >= high) {
            return Outcome.ANSWER;
        }
        if (topScore >= low) {
            return Outcome.CLARIFY;
        }
        return Outcome.REFUSE;
    }
}
