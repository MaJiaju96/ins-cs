package com.inscs.bot.web;

import java.util.UUID;

/** HTTP 层的两个 id 规则，放一处，免得两个控制器各写一遍。 */
final class Ids {

    private Ids() {
    }

    /** 16 位十六进制：够短（能塞进日志行）、够随机（一轮一个，不要求全局唯一）。 */
    static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** 前端没给会话 id 时，用本轮 trace 派生一个：一轮自成一会话，是合法状态而不是错误。 */
    static String conversationId(String requested, String traceId) {
        return requested == null || requested.isBlank() ? "c-" + traceId : requested;
    }
}
