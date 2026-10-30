package com.inscs.bot.demo;

import java.util.List;

/**
 * 演示数据：<b>全部虚构</b>。
 *
 * <p>不出现任何真实公司名、真实产品条款、真实保单号。规矩要一开始就立住：
 * 这是个人项目，演示数据只服务于「链路能不能跑通」，不承载业务真实性。
 *
 * <p>P1 接 MariaDB 后，这些常量会被 {@code V2__demo_seed.sql} 取代，
 * 但字段形状（订单号、产品名、保费、保障责任、材料清单）保持一致。
 */
public final class DemoData {

    public static final String PRODUCT_NAME = "安心出行意外险（演示）";
    public static final String PREMIUM = "199.00";
    public static final String COVERAGE = "意外身故/伤残 50 万，意外医疗 2 万，住院津贴 100 元/天";

    public static final String ORDER_NO = "A1024";
    public static final String ORDER_STATUS = "已生效";
    public static final String ORDER_PAID_AT = "2026-03-01";
    public static final String POLICY_NO = "P2026-0317-88";

    /** 犹豫期天数：犹豫期内全额退，之后按未满期净保费退。 */
    public static final int COOLING_OFF_DAYS = 15;

    public static final List<String> CLAIM_MATERIALS = List.of(
            "理赔申请书（线上填，无需打印）",
            "被保险人身份证明",
            "二级及以上医院出具的诊断证明与病历",
            "医疗费用发票原件与费用清单",
            "意外事故证明（如交通事故认定书）"
    );

    private DemoData() {
    }
}
