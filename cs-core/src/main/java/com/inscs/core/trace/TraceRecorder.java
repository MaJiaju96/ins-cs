package com.inscs.core.trace;

import com.inscs.core.ir.Span;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 单轮 trace 收集器。
 *
 * <p>用 {@link Span} 不可变记录 + 这个可变收集器，而不是让 Span 自己也变成可变对象：
 * 落库时需要的是「已经定型的一条事实」，不是「还在被改的对象」。
 *
 * <p>用法：
 * <pre>{@code
 * TraceRecorder t = new TraceRecorder(traceId);
 * Span route = t.addCompleted(null, Span.Type.ROUTE, "triage", 12L, Span.Status.OK, null);
 * String answer = t.record(route.spanId(), Span.Type.MODEL, "llm.main", () -> llm.generate(...));
 * }</pre>
 *
 * <p>关键约定：<b>异常不会丢掉 span</b>。{@link #record} 捕获异常后先记一个 ERROR span，
 * 再把异常原样抛出——否则线上最需要看的那一轮恰好什么都没有。
 */
public final class TraceRecorder {

    private final String traceId;
    private final List<Span> spans = new ArrayList<>();
    private int counter;

    public TraceRecorder(String traceId) {
        this.traceId = traceId;
    }

    public String traceId() {
        return traceId;
    }

    public synchronized String nextSpanId() {
        return "s" + (++counter);
    }

    /** 记一个已经完成的 span（不包住任何调用，例如分流结果）。 */
    public synchronized Span addCompleted(String parentSpanId, Span.Type type, String name,
                                          long durationMs, Span.Status status, String error) {
        long end = System.currentTimeMillis();
        Span span = new Span(nextSpanId(), parentSpanId, type, name, end - durationMs, end,
                null, status, java.util.Map.of(), error);
        spans.add(span);
        return span;
    }

    /** 包住一次调用，自动计时、自动记状态、异常照常抛出。 */
    public <T> T record(String parentSpanId, Span.Type type, String name, Supplier<T> body) {
        long start = System.currentTimeMillis();
        try {
            T value = body.get();
            append(parentSpanId, type, name, start, null, Span.Status.OK, null);
            return value;
        } catch (RuntimeException e) {
            append(parentSpanId, type, name, start, null, Span.Status.ERROR, e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            throw e;
        }
    }

    /** 同 {@link #record}，无返回值。 */
    public void recordVoid(String parentSpanId, Span.Type type, String name, Runnable body) {
        record(parentSpanId, type, name, () -> {
            body.run();
            return Boolean.TRUE;
        });
    }

    /** 记一个降级 span：这条链路换了假设，但没有失败。 */
    public Span degrade(String parentSpanId, Span.Type type, String name, String why) {
        long now = System.currentTimeMillis();
        Span span = new Span(nextSpanId(), parentSpanId, type, name, now, now, null,
                Span.Status.DEGRADED, java.util.Map.of("why", why), null);
        synchronized (this) {
            spans.add(span);
        }
        return span;
    }

    public synchronized List<Span> spans() {
        return List.copyOf(spans);
    }

    private synchronized void append(String parentSpanId, Span.Type type, String name,
                                     long start, Long firstToken, Span.Status status, String error) {
        long end = System.currentTimeMillis();
        spans.add(new Span(nextSpanId(), parentSpanId, type, name, start, end, firstToken,
                status, java.util.Map.of(), error));
    }
}
