package com.inscs.core.port;

import com.inscs.core.ir.TurnResult;

/**
 * trace 落库端口。
 *
 * <p><b>MariaDB 是唯一事实源。</b>外部可观测平台（Langfuse 等）只是旁路导出、允许丢。
 * 这条边界必须硬：评测要 GROUP BY、要 join、要算 recall，那是分析型查询，
 * 压在 trace 平台上跑不动，也不能接受「采样丢了一半」。
 *
 * <p>写入要求：
 * <ul>
 *   <li>一次轮次 = 一次批量写（turn 1 行 + span N 行），不要逐 span 往返；</li>
 *   <li>失败只记日志，不影响用户回复——trace 是旁路；</li>
 *   <li>span 的 {@code attributes} 存 JSON 列，别拆成列——属性会一直长。</li>
 * </ul>
 */
@FunctionalInterface
public interface TraceSink {

    void accept(TurnResult result);
}
