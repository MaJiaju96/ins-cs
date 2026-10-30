-- ============================================================================
-- 演示种子数据：全部虚构。
-- 与 cs-bot 里 com.inscs.bot.demo.DemoData 的常量保持一致；
-- P1 接库后 DemoData 退场，这些行成为唯一数据源。
--
-- 规矩：不出现任何真实公司名、真实产品、真实条款编号体系。
-- 条款编号（退保规则 2.3 等）是自己编的，只为演示「带引用的回答」长什么样。
-- ============================================================================

INSERT INTO product (product_code, name, premium_amount, coverage_summary, cooling_off_days)
VALUES ('acc-001', '安心出行意外险（演示）', 199.00,
        '意外身故/伤残 50 万，意外医疗 2 万，住院津贴 100 元/天', 15);

INSERT INTO ins_order (order_no, product_code, user_id, status, paid_at, premium_amount)
VALUES ('A1024', 'acc-001', 'u-1', 'EFFECTIVE', '2026-03-01 10:20:00.000', 199.00);

INSERT INTO policy (policy_no, order_no, product_code, effective_at, expire_at, status)
VALUES ('P2026-0317-88', 'A1024', 'acc-001',
        '2026-03-01 00:00:00.000', '2027-02-28 23:59:59.000', 'EFFECTIVE');

-- 分流规则：与 InMemoryRouteRuleProvider 一一对应。
-- priority 越小越先匹配；数字菜单必须比关键字优先，否则「2」会被别的规则抢走。
INSERT INTO route_rule (id, priority, match_kind, pattern, target_domain, intent, confidence, fallback_msg_key, enabled, note)
VALUES ('kw.human', 5, 'KEYWORD', '转人工', 'broker', 'handoff', 1.00, 'broker.error', 1,
        '用户明确要人，直接交接，不要试图挽留'),
       ('menu.order', 10, 'EXACT', '1', 'broker', 'order.query', 1.00, 'broker.error', 1,
        '反问菜单：回数字走确定性分支，不过模型'),
       ('menu.surrender', 10, 'EXACT', '2', 'broker', 'policy.surrender', 1.00, 'broker.error', 1,
        '反问菜单'),
       ('menu.claim', 10, 'EXACT', '3', 'broker', 'claim.materials', 1.00, 'broker.error', 1,
        '反问菜单'),
       ('order.no', 20, 'REGEX', '[Aa]\\d{4,}', 'broker', 'order.query', 1.00, 'broker.error', 1,
        '订单号形态足够特征化，正则命中比模型便宜三个数量级'),
       ('kw.surrender', 30, 'KEYWORD', '退保', 'broker', 'policy.surrender', 1.00, 'broker.error', 1,
        '高频意图，先走规则'),
       ('kw.refund', 31, 'KEYWORD', '退钱', 'broker', 'policy.surrender', 1.00, 'broker.error', 1,
        '用户口语，「退钱」≈退保/退款'),
       ('kw.claim', 40, 'KEYWORD', '理赔', 'broker', 'claim.materials', 1.00, 'broker.error', 1, NULL),
       ('kw.material', 41, 'KEYWORD', '材料', 'broker', 'claim.materials', 1.00, 'broker.error', 1, NULL),
       ('kw.order', 50, 'KEYWORD', '订单', 'broker', 'order.query', 1.00, 'broker.error', 1, NULL);

-- 预留但未启用的子域规则：P1 接入延保/商家险时只需把 enabled 改 1，
-- 并注册对应 SubAgent。没有 Agent 时命中这些规则会走 route.miss 降级 —— 这是设计好的出口。
INSERT INTO route_rule (id, priority, match_kind, pattern, target_domain, intent, confidence, fallback_msg_key, enabled, note)
VALUES ('kw.extwarranty', 60, 'KEYWORD', '延保', 'extwarranty', 'extwarranty.query', 1.00, 'route.miss', 0,
        '未启用子域：故意留一条 disabled 规则证明扩展口存在'),
       ('kw.merchant', 61, 'KEYWORD', '商家险', 'merchant', 'merchant.query', 1.00, 'route.miss', 0,
        '未启用子域');
