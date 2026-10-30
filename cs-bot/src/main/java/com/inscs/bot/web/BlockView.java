package com.inscs.bot.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.inscs.core.ir.Block;

import java.util.List;
import java.util.Map;

/**
 * 输出块<b>线格式</b>。
 *
 * <p>为什么要多一层，而不是直接把 {@link Block} 序列化出去：
 * 领域类型会演化（加字段、改语义），但前端契约不能跟着抖。
 * 这一层是「内部可以动、外部不许坏」的那道墙，也是参考系统里 ResponseTransformer 真正值钱的地方——
 * 只是我们只需要一个，不需要工厂。
 *
 * <p>约定：空字段不输出（{@code @JsonInclude(NON_NULL)}），线格式里没有 {@code "text": null} 这种噪音。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlockView(
        String kind,
        String text,
        String cardType,
        Map<String, Object> data,
        List<String> options,
        String reason,
        String summary,
        String queue
) {

    public static BlockView of(Block b) {
        // 用 instanceof 链而不是 switch 模式匹配：JDK 17 上 switch over sealed 还是 preview，
        // 而 preview 会污染构建参数。——等 JDK 21 再换语法，这一层不依赖语法的先进性。
        if (b instanceof Block.Text t) {
            return new BlockView("text", t.text(), null, null, null, null, null, null);
        }
        if (b instanceof Block.Card c) {
            return new BlockView("card", null, c.cardType(), c.data(), null, null, null, null);
        }
        if (b instanceof Block.Suggestion s) {
            return new BlockView("suggestion", null, null, null, s.options(), null, null, null);
        }
        if (b instanceof Block.Thinking t) {
            return new BlockView("thinking", t.text(), null, null, null, null, null, null);
        }
        if (b instanceof Block.Handoff h) {
            return new BlockView("handoff", null, null, null, null, h.reason(), h.summary(), h.queue());
        }
        if (b instanceof Block.Hidden h) {
            return new BlockView("hidden", null, null, h.data(), null, null, null, null);
        }
        throw new IllegalStateException("unmapped block type: " + b.getClass().getName());
    }

    public static List<BlockView> of(List<Block> blocks) {
        return blocks.stream().map(BlockView::of).toList();
    }
}
