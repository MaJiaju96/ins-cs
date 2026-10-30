package com.inscs.bot.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.inscs.core.ir.RunTrace;
import com.inscs.core.ir.Span;
import com.inscs.core.ir.TurnResult;

import java.util.List;

/**
 * 一轮对话响应（线格式）。
 *
 * <p>故意把 {@code route} 与 {@code spans} 一起返回：前端能看见「它为什么这么答」，
 * 调试时不用去翻日志，评测台也能直接拿这一份做断言。
 * 这是刻意的信息暴露——内部系统里这类信息往往只在日志里，于是谁都说不清线上发生了什么。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(
        String traceId,
        String conversationId,
        long seq,
        RouteView route,
        List<BlockView> blocks,
        UsageView usage,
        List<SpanView> spans
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RouteView(String domain, String intent, String state, double confidence, String ruleId) {
    }

    public record UsageView(int promptTokens, int completionTokens) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SpanView(String type, String name, long durationMs, String status, String error) {
    }

    public static ChatResponse of(TurnResult r) {
        return new ChatResponse(
                r.traceId(),
                r.conversationId(),
                r.seq(),
                new RouteView(r.route().domain().code(), r.route().intent(),
                        r.route().state().name(), r.route().confidence(), r.route().ruleId()),
                BlockView.of(r.blocks()),
                new UsageView(r.usage().promptTokens(), r.usage().completionTokens()),
                spans(r.trace()));
    }

    private static List<SpanView> spans(RunTrace trace) {
        return trace.spans().stream()
                .map(s -> new SpanView(s.type().name(), s.name(),
                        s.durationMs() == null ? 0L : s.durationMs(), s.status().name(), s.error()))
                .toList();
    }
}
