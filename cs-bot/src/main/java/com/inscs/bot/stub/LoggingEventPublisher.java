package com.inscs.bot.stub;

import com.inscs.core.port.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P0 事件桩：打到日志。
 *
 * <p>P1 换成 RabbitMQ 时，主题名与消息体形状不要动——卫星系统（badcase 收集箱）
 * 就是按这两个东西接的。事件体保持扁平的小 Map，别塞整个 TurnResult。
 */
public final class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger("ins-cs.event");

    @Override
    public void publish(String topic, Object payload) {
        log.info("topic={} payload={}", topic, payload);
    }
}
