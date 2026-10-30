package com.inscs.bot.stub;

import com.inscs.core.ir.Span;
import com.inscs.core.ir.TurnResult;
import com.inscs.core.port.TraceSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P0 落库桩：把 trace 打到日志，一行一轮。
 *
 * <p>这不是「先凑合」，而是故意的顺序：<b>先把 trace 的形状定下来，再决定往哪写。</b>
 * 格式定得越早，P1 换 MariaDB 时越不需要改上游。
 *
 * <p>日志行就是未来 turn 表的行：traceId | seq | domain | state | blocks | tokens | 耗时 | span 摘要。
 */
public final class LoggingTraceSink implements TraceSink {

    private static final Logger log = LoggerFactory.getLogger("ins-cs.trace");

    @Override
    public void accept(TurnResult r) {
        StringBuilder spans = new StringBuilder();
        for (Span s : r.trace().spans()) {
            if (spans.length() > 0) {
                spans.append(',');
            }
            spans.append(s.type()).append(':').append(s.name())
                    .append('=').append(s.status());
        }
        log.info("traceId={} conv={} seq={} domain={} intent={} state={} conf={} blocks={} tokens={} spans=[{}]",
                r.traceId(),
                r.conversationId(),
                r.seq(),
                r.route().domain().code(),
                r.route().intent(),
                r.route().state(),
                r.route().confidence(),
                r.blocks().size(),
                r.usage().total(),
                spans);
    }
}
