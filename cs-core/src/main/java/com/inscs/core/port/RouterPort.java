package com.inscs.core.port;

import com.inscs.core.ir.RouteDecision;
import com.inscs.core.ir.TurnContext;

/**
 * 小模型分流端口（规则没命中时的兜底）。
 *
 * <p>实现要求：
 * <ul>
 *   <li>低温度（0.1）+ JSON 输出，别让它自由发挥；</li>
 *   <li>必须给出置信度，低于 {@link RouteDecision#MIN_MODEL_CONFIDENCE} 由上层改成反问；</li>
 *   <li>失败时抛 {@link RuntimeException}（超时/解析失败），<b>不要</b>返回一个猜测——
 *       上层会把异常转成反问，而返回猜测会变成一次错误分流。</li>
 * </ul>
 */
public interface RouterPort {

    RouteDecision classify(TurnContext ctx);
}
