package com.inscs.bot.stub;

import com.inscs.core.ir.Domain;
import com.inscs.core.ir.RouteDecision;
import com.inscs.core.ir.TurnContext;
import com.inscs.core.port.RouterPort;

/**
 * P0 分流兜底桩：不做任何模型调用，直接反问。
 *
 * <p>这是有意为之的「最保守实现」。它证明了一件事：
 * <b>把模型拿掉，系统仍然能工作</b>——分流退化成反问，用户拿到编号菜单，
 * 选「2」就走确定性分支。等到接入真模型，只是让这一层少问一次。
 *
 * <p>P1 换成 LLM 实现时，务必保留「异常上抛 → 上层转反问」的契约。
 */
public final class AskBackRouter implements RouterPort {

    @Override
    public RouteDecision classify(TurnContext ctx) {
        return RouteDecision.askBack(Domain.ENTRY);
    }
}
