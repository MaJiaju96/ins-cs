package com.inscs.core.ir;

/**
 * 业务域。对应「路由的终点」。
 *
 * <p>设计要点：域是**插件单位**，不是 if-else 分支。新增一个域 = 新增一个 {@code SubAgent} 实现
 * 并注册一个 {@code route_rule} 行；未实现/未启用的域必须优雅降级（记 route.miss），不能让请求报错。
 */
public enum Domain {

    /** 入口域：基础问答、意图判定、拒识兜底。永远在线。 */
    ENTRY("entry", "入口：基础问答、意图判定、拒识兜底"),

    /** 经纪客服：P1 唯一要真正实现的域。 */
    BROKER("broker", "经纪客服：知识库问答、订单、退费、退保、基础理赔"),

    /** 延保：只留扩展出口，P1 不实现。 */
    EXTWARRANTY("extwarranty", "延保：预留出口"),

    /** 商家险：只留扩展出口，P1 不实现。 */
    MERCHANT("merchant", "商家险：预留出口");

    private final String code;
    private final String description;

    Domain(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static Domain of(String code) {
        for (Domain d : values()) {
            if (d.code.equals(code)) {
                return d;
            }
        }
        throw new IllegalArgumentException("unknown domain code: " + code);
    }
}
