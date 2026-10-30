package com.inscs.core.ir;

import java.util.List;

/**
 * 一轮对话的完整结果 = 知道什么（route）+ 说了什么（blocks）+ 做了什么（trace）+ 花了多少（usage）。
 *
 * <p>这四个字段刻意放在同一个对象里：只要拿到一个 {@link TurnResult}，
 * 评测台就能判断这一轮对不对、贵不贵、慢不慢，不需要再去 join 别的东西。
 * 它同时是落库单位、回放单位、导出单位。
 *
 * @param seq 与 {@link TurnContext#seq()} 一致，会话内的权威序
 */
public record TurnResult(
        String traceId,
        String conversationId,
        long seq,
        RouteDecision route,
        List<Block> blocks,
        Usage usage,
        RunTrace trace
) {

    /** token 用量。成本按它算，不按字符数估。 */
    public record Usage(int promptTokens, int completionTokens) {

        public static final Usage ZERO = new Usage(0, 0);

        public int total() {
            return promptTokens + completionTokens;
        }
    }

    public TurnResult {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        usage = usage == null ? Usage.ZERO : usage;
        trace = trace == null ? new RunTrace(traceId, List.of()) : trace;
    }

    /** 只取正文块并拼起来，供日志、关键词断言、标题生成使用。 */
    public String plainText() {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) {
            if (b instanceof Block.Text t) {
                sb.append(t.text());
            }
        }
        return sb.toString();
    }

    public boolean hasHandoff() {
        return blocks.stream().anyMatch(b -> b instanceof Block.Handoff);
    }

    public boolean hasSuggestion() {
        return blocks.stream().anyMatch(b -> b instanceof Block.Suggestion);
    }
}
