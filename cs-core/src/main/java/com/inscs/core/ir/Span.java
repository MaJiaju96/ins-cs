package com.inscs.core.ir;

import java.util.Map;

/**
 * 过程侧：一个 span。
 *
 * <p>{@link #firstTokenMs} 是刻意单列的：用户感知的「慢」和系统总耗时不是一回事。
 * 总耗时 8 秒但首字 0.9 秒，体验是「快」；反之是「卡」。弱网场景下这是第一优先指标，
 * 也是评测台可以直接断言的一条 SLO。
 *
 * @param spanId       本次 trace 内唯一
 * @param parentSpanId 父 span，根为 null
 * @param endMs        未结束时为 null
 * @param firstTokenMs 首 token 相对 span 开始的毫秒数，只对模型类 span 有意义
 * @param attributes   结构化附加信息（命中规则、topK、分数、token 数、降级级别）
 * @param error        失败原因，成功为 null
 */
public record Span(
        String spanId,
        String parentSpanId,
        Type type,
        String name,
        long startMs,
        Long endMs,
        Long firstTokenMs,
        Status status,
        Map<String, Object> attributes,
        String error
) {

    /** span 类型。只留这几类，是为了让评测台能按类型聚合耗时与失败率。 */
    public enum Type {
        /** 分流决策。 */
        ROUTE,
        /** 模型调用。 */
        MODEL,
        /** 检索。 */
        RETRIEVAL,
        /** 业务工具（查订单、试算）。 */
        TOOL,
        /** 转人工。 */
        HANDOFF,
        /** 出站渲染。 */
        RENDER
    }

    /**
     * 状态。
     *
     * <p>{@link #DEGRADED} 是刻意独立的状态：降级成功不是干净的成功，
     * 它必须能被统计成一条指标（降级率），否则降级链会悄悄腐烂。
     */
    public enum Status {
        OK, ERROR, DEGRADED, SKIPPED
    }

    public Span {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** 耗时；未结束返回 null。 */
    public Long durationMs() {
        return endMs == null ? null : endMs - startMs;
    }

    public boolean isRoot() {
        return parentSpanId == null;
    }
}
