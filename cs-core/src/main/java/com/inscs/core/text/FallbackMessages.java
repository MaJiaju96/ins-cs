package com.inscs.core.text;

import java.util.Map;

/**
 * 降级话术表（骨架版：写死在代码里）。
 *
 * <p>P1 会把它挪进 {@code message_template} 表 + Nacos 灰度，但<b>键名不动</b>——
 * 键是接口，文案是数据。规则表、trace、单测都按 key 引用，文案可以随便改。
 *
 * <p>为什么要「每个域自带降级话术」而不是全局一句「系统繁忙」：
 * 用户在问退保时听到「系统繁忙」会重试，听到「退保规则我这边暂时查不到，
 * 先把入口给你，你可以自助办理」会自己走掉——后者才是降级该有的效果。
 */
public final class FallbackMessages {

    public static final String ROUTE_MISS = "route.miss";
    public static final String ROUTE_CLARIFY = "route.clarify";
    public static final String BROKER_ERROR = "broker.error";
    public static final String RETRIEVAL_LOW_CONFIDENCE = "retrieval.low_confidence";
    public static final String RETRIEVAL_EMPTY = "retrieval.empty";
    public static final String DEFAULT = "default";

    private static final Map<String, String> TEXTS = Map.of(
            ROUTE_MISS,
            "这块业务我暂时还办不了，我先帮你转接人工客服，免得让你来回试。",
            ROUTE_CLARIFY,
            "想确认一下你具体要办哪件事：1. 查订单 2. 退保 3. 理赔材料 4. 其他问题（回数字就行）",
            BROKER_ERROR,
            "这条我这边没查到准确结果，先不给你猜。你可以把订单号发我，或者直接转人工。",
            RETRIEVAL_LOW_CONFIDENCE,
            "我找到几个相关条款，但不敢确定哪个对得上，你选一个我细说：",
            RETRIEVAL_EMPTY,
            "条款库里没有匹配到这一条，这个问题我需要转人工确认，避免给你错的答案。",
            DEFAULT,
            "这个我暂时回答不了，帮你转人工更稳妥。"
    );

    private FallbackMessages() {
    }

    public static String of(String key) {
        if (key == null || key.isBlank()) {
            return TEXTS.get(DEFAULT);
        }
        return TEXTS.getOrDefault(key, TEXTS.get(DEFAULT));
    }
}
