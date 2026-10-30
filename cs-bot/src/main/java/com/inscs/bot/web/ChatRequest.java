package com.inscs.bot.web;

/**
 * 一轮对话请求（线格式）。
 *
 * @param conversationId 会话 id，由前端生成；第一轮也必须带上（我们不替客户端发号）
 * @param seq            会话内序号，从 1 开始递增。<b>seq 是权威序</b>——不靠时间戳排序，
 *                       因为多源合并时时间戳会打架（参考系统就栽在这上面）
 * @param userId         用户标识；演示阶段随意，生产要脱敏
 * @param channel        渠道：web / app / miniapp
 * @param message        用户输入
 */
public record ChatRequest(
        String conversationId,
        Long seq,
        String userId,
        String channel,
        String message
) {

    public long seqOrDefault() {
        return seq == null || seq < 1 ? 1L : seq;
    }

    public String channelOrDefault() {
        return channel == null || channel.isBlank() ? "web" : channel;
    }

    public String userIdOrDefault() {
        return userId == null || userId.isBlank() ? "anonymous" : userId;
    }
}
