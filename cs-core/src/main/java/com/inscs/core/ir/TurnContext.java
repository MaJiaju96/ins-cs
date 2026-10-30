package com.inscs.core.ir;

import java.util.List;
import java.util.Map;

/**
 * 输入侧：这一轮对话我们<b>知道什么</b>。
 *
 * <p>三个 IR 层按「知道 / 做了 / 说了」切分，而不是按信息丰富度切：
 * <ul>
 *   <li>{@code TurnContext}（本类）= 知道什么：输入、历史、路由结论、一次查全的业务快照；</li>
 *   <li>{@link RunTrace} = 做了什么：span 树；</li>
 *   <li>{@link Block} 列表 = 说了什么：文本/卡片/追问/转人工。</li>
 * </ul>
 * 这样切的好处是三层有独立生命周期和独立事实源：本类随请求生灭，trace 要落库回放，blocks 要被前端与评测台同时消费。
 *
 * <p><b>关键约束（换往返）：</b>{@link #bizContext()} 在入口层一次查全（用户、订单、保单、产品），
 * 下游所有子 Agent 只读内存、不许回查数据库。上下文几百字节，一次 DB 往返 40–80ms —— 用冗余换往返。
 *
 * @param traceId        链路 id，在 Controller 入口生成，第一跳就注入，事后再补没有位置
 * @param conversationId 会话 id
 * @param seq            会话内单调递增轮次号，多源合并时的唯一权威序
 * @param channel        渠道标识（本期只有 web）
 * @param userId         用户标识（虚构用户）
 * @param userInput      本轮用户输入原文
 * @param history        最近若干轮历史，旧→新
 * @param route          分流结论；triage 之前为 null
 * @param bizContext     前置一次查全的业务快照，只读
 */
public record TurnContext(
        String traceId,
        String conversationId,
        long seq,
        String channel,
        String userId,
        String userInput,
        List<HistoryTurn> history,
        RouteDecision route,
        Map<String, Object> bizContext
) {

    /** 历史消息角色。只保留两种，避免把渠道私有角色泄漏进 IR。 */
    public enum Role {
        USER, ASSISTANT
    }

    /**
     * 一条历史消息。
     *
     * @param seq 该消息所属轮次号；合并多源时用它排序，不依赖时间戳
     */
    public record HistoryTurn(Role role, String text, long seq) {
    }

    public TurnContext {
        history = history == null ? List.of() : List.copyOf(history);
        bizContext = bizContext == null ? Map.of() : Map.copyOf(bizContext);
    }

    /** 最常用的构造：只有必需字段。 */
    public static TurnContext newTurn(String traceId, String conversationId, long seq,
                                      String channel, String userId, String userInput) {
        return new TurnContext(traceId, conversationId, seq, channel, userId, userInput,
                List.of(), null, Map.of());
    }

    public TurnContext withHistory(List<HistoryTurn> newHistory) {
        return new TurnContext(traceId, conversationId, seq, channel, userId, userInput,
                newHistory, route, bizContext);
    }

    public TurnContext withBizContext(Map<String, Object> snapshot) {
        return new TurnContext(traceId, conversationId, seq, channel, userId, userInput,
                history, route, snapshot);
    }

    public TurnContext withRoute(RouteDecision decision) {
        return new TurnContext(traceId, conversationId, seq, channel, userId, userInput,
                history, decision, bizContext);
    }

    /** 历史里最近 N 条，旧→新。意图识别只喂 5 条，别把整段塞给模型。 */
    public List<HistoryTurn> recentHistory(int n) {
        if (n <= 0 || history.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, history.size() - n);
        return history.subList(from, history.size());
    }
}
