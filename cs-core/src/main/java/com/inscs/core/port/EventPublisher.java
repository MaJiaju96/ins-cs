package com.inscs.core.port;

/**
 * 事件出口。卫星系统（badcase 收集箱、轨迹回放、评测台、看板）只订阅事件、不侵入主链路。
 *
 * <p>事件是最小契约：<b>只发「发生了什么」，不发「你应该做什么」</b>。
 * 一旦事件里带上指令语义，卫星就会开始反向要求主链路改字段——那时主线就被卫星绑架了。
 *
 * <p>发送纪律：
 * <ul>
 *   <li>异步、失败只记日志，绝不影响用户回复；</li>
 *   <li>消息体 ≤64KB，超限的负载存 MinIO 只发 key；</li>
 *   <li>至少一次投递，消费方必须幂等（按 {@code traceId + seq} 去重）。</li>
 * </ul>
 */
public interface EventPublisher {

    void publish(String topic, Object payload);

    /** 事件主题常量。名字即契约，改动等于破接口。 */
    final class Topics {
        /** 一轮对话完成。卫星据此建 badcase、做回放。 */
        public static final String TURN_COMPLETED = "cs.turn.completed";
        /** 工具调用失败。 */
        public static final String TOOL_FAILED = "cs.tool.failed";
        /** 转人工，带交接摘要包。 */
        public static final String HANDOFF_CREATED = "cs.handoff.created";
        /** 人工标记 badcase。 */
        public static final String BADCASE_MARKED = "cs.badcase.marked";
        /** 分流未命中：路由判到了某域，但该域没有实现（或未启用）。 */
        public static final String ROUTE_MISS = "cs.route.miss";

        private Topics() {
        }
    }
}
