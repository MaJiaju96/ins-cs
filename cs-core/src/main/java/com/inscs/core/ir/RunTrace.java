package com.inscs.core.ir;

import java.util.List;
import java.util.Optional;

/**
 * 过程侧：一轮对话的 span 树。
 *
 * <p><b>这是我们相对参考系统最重要的差异点。</b>参考系统把运行上下文只放在内存里，
 * 进程重启即丢，事后无法回答「上周那 200 个会话为什么答错」。我们把它当成一等公民：
 * 落 MariaDB、可回放、可 diff、可喂给评测台。
 *
 * <p>事实源只有一个：MariaDB 的 {@code turn} / {@code span}。
 * 外部可观测平台（Langfuse 等）只是旁路导出、允许丢——因为评测要 GROUP BY、要 join、要算 recall，
 * 那是分析型查询，不该压在外部 trace 平台上。
 */
public record RunTrace(String traceId, List<Span> spans) {

    public RunTrace {
        spans = spans == null ? List.of() : List.copyOf(spans);
    }

    public Optional<Span> root() {
        return spans.stream().filter(Span::isRoot).findFirst();
    }

    public List<Span> childrenOf(String parentSpanId) {
        return spans.stream()
                .filter(s -> java.util.Objects.equals(s.parentSpanId(), parentSpanId))
                .toList();
    }

    public List<Span> byType(Span.Type type) {
        return spans.stream().filter(s -> s.type() == type).toList();
    }

    /** 模型类 span 的总耗时，用来回答「这一轮的钱和时间花在哪」。 */
    public long totalModelMs() {
        return spans.stream()
                .filter(s -> s.type() == Span.Type.MODEL)
                .filter(s -> s.durationMs() != null)
                .mapToLong(Span::durationMs)
                .sum();
    }

    public long degradedCount() {
        return spans.stream().filter(s -> s.status() == Span.Status.DEGRADED).count();
    }

    public boolean hasError() {
        return spans.stream().anyMatch(s -> s.status() == Span.Status.ERROR);
    }
}
