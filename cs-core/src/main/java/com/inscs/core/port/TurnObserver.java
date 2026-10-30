package com.inscs.core.port;

import com.inscs.core.ir.Block;
import com.inscs.core.ir.RouteDecision;
import com.inscs.core.ir.TurnContext;
import com.inscs.core.ir.TurnResult;

/**
 * 一轮执行过程的观察者：分流定了 → 冒一个 block → 这一轮结束。
 *
 * <p>存在的理由只有一个：<b>让已经想清楚的部分先到用户眼前</b>。
 * 在 2 Mbps 链路上，把 5 个 block 攒到最后一次性发出去，用户感受到的是
 * 「转圈一会儿，然后刷出一屏」；分开推，至少「我认出你要退保」这半句能先到。
 *
 * <p>三类回调的语义差别：
 * <ul>
 *   <li>{@link #onRoute} 一定在 {@link #onBlock} 之前——分流是一轮里最早确定的事；</li>
 *   <li>{@link #onBlock} 的调用顺序就是最终 blocks 的顺序，可以多次；</li>
 *   <li>{@link #onComplete} 带完整 {@link TurnResult}，是<b>唯一适合落库的时机</b>。</li>
 * </ul>
 *
 * <p>这不是「token 流」。token 流要等接真模型时从 {@code LlmPort} 往里灌，
 * 结构上落的是同一个观察者——所以现在先把它定下来，而不是等接模型时再改签名。
 *
 * <p>实现方必须自己吞掉 IO 异常：观察者抛错不能影响用户拿到回答。
 */
public interface TurnObserver {

    default void onRoute(TurnContext ctx, RouteDecision route) {
    }

    default void onBlock(Block block) {
    }

    default void onComplete(TurnResult result) {
    }

    /** 不关心过程的调用方（同步 JSON 接口、单测）用它。 */
    TurnObserver NOOP = new TurnObserver() {
    };
}
