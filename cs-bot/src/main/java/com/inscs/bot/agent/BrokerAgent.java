package com.inscs.bot.agent;

import com.inscs.bot.demo.DemoData;
import com.inscs.core.agent.SubAgent;
import com.inscs.core.ir.Block;
import com.inscs.core.ir.Domain;
import com.inscs.core.ir.RunTrace;
import com.inscs.core.ir.Span;
import com.inscs.core.ir.TurnContext;
import com.inscs.core.ir.TurnResult;
import com.inscs.core.text.FallbackMessages;
import com.inscs.core.trace.TraceRecorder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 经纪客服域 Agent（P0）。
 *
 * <p>P0 版本<b>不调模型</b>，全部走确定性分支：查订单 → 卡片；退保 → 条款 + 选项；理赔 → 材料清单；
 * 转人工 → 交接包。这样做不是偷懒，而是先把「结构对不对」验证掉：
 * 如果分流的四态、卡片的形状、trace 的粒度都设计错了，接模型只会把错误放大。
 *
 * <p>P1 会把「退保损失试算」和「条款问答」换成 LLM + 检索，但保留三条纪律：
 * <ol>
 *   <li>每轮至少一个 TOOL span，否则事后看不清它做了什么；</li>
 *   <li>不确定就给编号候选，不要编；</li>
 *   <li>需要人接手时给交接包（Handoff），让坐席不用重问一遍。</li>
 * </ol>
 */
@Component
public final class BrokerAgent implements SubAgent {

    private static final Pattern ORDER_NO = Pattern.compile("[Aa]\\d{4,}");

    @Override
    public Domain domain() {
        return Domain.BROKER;
    }

    @Override
    public boolean canHandle(TurnContext ctx) {
        return ctx.route() != null && ctx.route().intent() != null;
    }

    @Override
    public TurnResult handle(TurnContext ctx) {
        TraceRecorder rec = new TraceRecorder(ctx.traceId());
        List<Block> blocks = switch (ctx.route().intent()) {
            case "order.query" -> order(ctx, rec);
            case "policy.surrender" -> surrender(rec);
            case "claim.materials" -> claimMaterials(rec);
            case "handoff" -> handoff(ctx, rec);
            default -> unknown(rec);
        };
        return new TurnResult(ctx.traceId(), ctx.conversationId(), ctx.seq(), ctx.route(), blocks,
                TurnResult.Usage.ZERO, new RunTrace(ctx.traceId(), rec.spans()));
    }

    private List<Block> order(TurnContext ctx, TraceRecorder rec) {
        String asked = extractOrderNo(ctx.userInput());
        List<Block> out = new ArrayList<>();

        if (asked == null) {
            out.add(new Block.Text("把订单号发我就行（形如 A1024），我直接给你查。"));
            out.add(new Block.Suggestion(List.of("示例订单 " + DemoData.ORDER_NO, "转人工")));
            rec.degrade(null, Span.Type.TOOL, "order.query", "no order no in input");
            return out;
        }

        boolean found = rec.record(null, Span.Type.TOOL, "order.query",
                () -> asked.equalsIgnoreCase(DemoData.ORDER_NO));

        if (!found) {
            out.add(new Block.Text("没查到订单 " + asked + "。确认下订单号，或者我帮你转人工。"));
            out.add(new Block.Suggestion(List.of("重新输入订单号", "转人工")));
            return out;
        }

        out.add(new Block.Text("查到了，" + DemoData.ORDER_NO + " 的订单信息如下："));
        out.add(new Block.Card("order", Map.of(
                "orderNo", DemoData.ORDER_NO,
                "product", DemoData.PRODUCT_NAME,
                "status", DemoData.ORDER_STATUS,
                "paidAt", DemoData.ORDER_PAID_AT,
                "premium", DemoData.PREMIUM,
                "policyNo", DemoData.POLICY_NO)));
        out.add(new Block.Suggestion(List.of("看保障责任", "怎么退保", "转人工")));
        return out;
    }

    private List<Block> surrender(TraceRecorder rec) {
        rec.recordVoid(null, Span.Type.TOOL, "surrender.quote",
                () -> sleepQuietly(2));
        return List.of(
                new Block.Text("退保能退多少，取决于是否在犹豫期内（依据：退保规则 2.3）：\n"
                        + "· 保单生效 " + DemoData.COOLING_OFF_DAYS + " 天内退保：全额退还已交保费；\n"
                        + "· 超过犹豫期：按未满期净保费退，会扣掉已保障天数的成本。\n"
                        + "如果你要，我可以直接帮你试算这次能退多少。"),
                new Block.Suggestion(List.of("帮我试算退保金额", "先看条款原文", "转人工")),
                new Block.Hidden(Map.of(
                        "intent", "policy.surrender",
                        "citation", "退保规则 2.3",
                        "next", "surrender.quote")));
    }

    private List<Block> claimMaterials(TraceRecorder rec) {
        rec.recordVoid(null, Span.Type.RETRIEVAL, "claim.materials", () -> sleepQuietly(1));
        StringBuilder sb = new StringBuilder("理赔材料清单如下（依据：理赔材料清单 1.1）：\n");
        for (int i = 0; i < DemoData.CLAIM_MATERIALS.size(); i++) {
            sb.append(i + 1).append(". ").append(DemoData.CLAIM_MATERIALS.get(i)).append('\n');
        }
        sb.append("材料齐了在「我的保单 - 申请理赔」里上传，一般 1 个工作日内给受理结果。");
        return List.of(
                new Block.Text(sb.toString()),
                new Block.Suggestion(List.of("我材料不齐怎么办", "什么情况不赔", "转人工")));
    }

    private List<Block> handoff(TurnContext ctx, TraceRecorder rec) {
        rec.degrade(null, Span.Type.HANDOFF, "handoff",
                "user requested");
        return List.of(
                new Block.Text("好，正在帮你转人工客服，稍等一下。"),
                new Block.Handoff(
                        "用户主动要求转人工",
                        "最近一轮用户说：" + abbrev(ctx.userInput())
                                + "；会话 seq=" + ctx.seq() + "，暂未涉及具体订单",
                        "broker-general"));
    }

    private List<Block> unknown(TraceRecorder rec) {
        rec.degrade(null, Span.Type.ROUTE, "broker.default", "intent not implemented in P0");
        return List.of(
                new Block.Text(FallbackMessages.of(FallbackMessages.BROKER_ERROR)),
                new Block.Suggestion(List.of("查订单", "退保", "理赔材料", "转人工")));
    }

    private static String extractOrderNo(String input) {
        if (input == null) {
            return null;
        }
        Matcher m = ORDER_NO.matcher(input);
        return m.find() ? m.group() : null;
    }

    private static String abbrev(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
