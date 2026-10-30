-- ============================================================================
-- ins-cs 核心库
-- 目标：MariaDB 10.11 / utf8mb4 / InnoDB
--
-- 设计约束（来自 2 Mbps 弱网链路）：
--   1. 一次对话轮次 = 1 行 turn + N 行 span，用 trace_id 关联，不做多表 join 回查
--   2. 结构化块与 span 属性一律存 JSON 列 —— 属性会一直长，不为它加列
--   3. 所有表都带 created_at，便于按时间分区/归档（弱网机器容量有限）
--   4. route 的判定结果必须落库（domain/intent/state/confidence/rule_id），
--      否则「路由命中率」「误问率」不可测 —— 这是与参考系统最大的不同
-- ============================================================================

CREATE TABLE conversation (
    conversation_id  VARCHAR(64)  NOT NULL COMMENT '会话 id，客户端生成',
    channel          VARCHAR(32)  NOT NULL DEFAULT 'web' COMMENT 'web/app/miniapp',
    user_id          VARCHAR(64)  NOT NULL COMMENT '用户标识（演示阶段随意，生产要脱敏）',
    started_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_turn_seq    BIGINT       NOT NULL DEFAULT 0 COMMENT '已发生的最新一轮序号',
    PRIMARY KEY (conversation_id),
    KEY idx_conv_user (user_id, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '会话';

-- 一轮对话。这是评测与分析的主表，也是唯一事实源。
CREATE TABLE turn (
    trace_id         VARCHAR(32)  NOT NULL COMMENT '一轮的 trace id',
    conversation_id  VARCHAR(64)  NOT NULL,
    seq              BIGINT       NOT NULL COMMENT '会话内权威序号（不靠时间戳排序）',
    channel          VARCHAR(32)  NOT NULL,
    user_id          VARCHAR(64)  NOT NULL,
    user_input       TEXT         NOT NULL,

    route_domain     VARCHAR(32)  NOT NULL COMMENT 'entry/broker/extwarranty/merchant',
    route_intent     VARCHAR(64)  NULL,
    route_state      VARCHAR(16)  NOT NULL COMMENT 'HIT_RULE/HIT_MODEL/ASK_BACK/DEGRADED',
    route_confidence DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
    rule_id          VARCHAR(64)  NULL COMMENT '命中的规则，模型/反问为 NULL',
    fallback_msg_key VARCHAR(64)  NULL COMMENT '该轮使用的降级话术键',

    blocks_json      JSON         NOT NULL COMMENT '输出块数组（线格式同构，但以 BlockView 冻结）',
    prompt_tokens    INT          NOT NULL DEFAULT 0,
    completion_tokens INT         NOT NULL DEFAULT 0,
    total_ms         INT          NOT NULL DEFAULT 0,
    degraded         TINYINT(1)   NOT NULL DEFAULT 0,

    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (trace_id),
    UNIQUE KEY uk_turn_conv_seq (conversation_id, seq),
    KEY idx_turn_created (created_at),
    KEY idx_turn_route (route_domain, route_state, created_at),
    KEY idx_turn_degraded (degraded, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '对话轮次（唯一事实源）';

-- span 树：做了什么、慢在哪、哪一步降级了
CREATE TABLE span (
    trace_id         VARCHAR(32)  NOT NULL,
    span_id          VARCHAR(16)  NOT NULL COMMENT 's1、s2…（单轮内唯一）',
    parent_span_id   VARCHAR(16)  NULL,
    span_type        VARCHAR(16)  NOT NULL COMMENT 'ROUTE/MODEL/RETRIEVAL/TOOL/HANDOFF/RENDER',
    name             VARCHAR(128) NOT NULL,
    start_ms         BIGINT       NOT NULL COMMENT 'epoch millis',
    end_ms           BIGINT       NULL,
    duration_ms      INT          NULL,
    first_token_ms   BIGINT       NULL COMMENT '首 token 时刻，用于流式体验指标',
    status           VARCHAR(16)  NOT NULL COMMENT 'OK/ERROR/DEGRADED/SKIPPED',
    attributes       JSON         NULL,
    error            VARCHAR(512) NULL,
    PRIMARY KEY (trace_id, span_id),
    KEY idx_span_type (span_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '单轮执行 span';

-- 分流规则：事实源在仓库（Flyway 迁移 + review），不靠配置中心热改
CREATE TABLE route_rule (
    id               VARCHAR(64)  NOT NULL COMMENT '规则 id，写进 trace 可反查',
    priority         INT          NOT NULL COMMENT '数字小的先匹配',
    match_kind       VARCHAR(16)  NOT NULL COMMENT 'KEYWORD/REGEX/EXACT',
    pattern          VARCHAR(255) NOT NULL,
    target_domain    VARCHAR(32)  NOT NULL,
    intent           VARCHAR(64)  NOT NULL,
    confidence       DECIMAL(3, 2) NOT NULL DEFAULT 1.00,
    fallback_msg_key VARCHAR(64)  NULL,
    enabled          TINYINT(1)   NOT NULL DEFAULT 1,
    note             VARCHAR(255) NULL COMMENT '为什么有这条规则（给三个月后的自己看）',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_rule_priority (enabled, priority)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '分流规则（结构进仓库，参数进配置中心）';

-- ---------------------------- 演示业务表（全虚构） ----------------------------

CREATE TABLE product (
    product_code     VARCHAR(32)  NOT NULL,
    name             VARCHAR(128) NOT NULL,
    premium_amount   DECIMAL(10, 2) NOT NULL,
    coverage_summary VARCHAR(512) NOT NULL,
    cooling_off_days INT          NOT NULL DEFAULT 15,
    enabled          TINYINT(1)   NOT NULL DEFAULT 1,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (product_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '产品（虚构）';

CREATE TABLE ins_order (
    order_no         VARCHAR(32)  NOT NULL,
    product_code     VARCHAR(32)  NOT NULL,
    user_id          VARCHAR(64)  NOT NULL,
    status           VARCHAR(16)  NOT NULL COMMENT 'PAID/EFFECTIVE/CANCELLED',
    paid_at          DATETIME(3)  NULL,
    premium_amount   DECIMAL(10, 2) NOT NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (order_no),
    KEY idx_order_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '订单（虚构）';

CREATE TABLE policy (
    policy_no        VARCHAR(32)  NOT NULL,
    order_no         VARCHAR(32)  NOT NULL,
    product_code     VARCHAR(32)  NOT NULL,
    effective_at     DATETIME(3)  NOT NULL,
    expire_at        DATETIME(3)  NOT NULL,
    status           VARCHAR(16)  NOT NULL COMMENT 'EFFECTIVE/SURRENDERED/EXPIRED',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (policy_no),
    KEY idx_policy_order (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '保单（虚构）';

-- badcase 收集箱。卫星系统的第一块砖，但表先建在这里：
-- 它是「人工判定」的入口，没有它，评测集只能靠编。
CREATE TABLE badcase (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trace_id         VARCHAR(32)  NOT NULL,
    conversation_id  VARCHAR(64)  NOT NULL,
    seq              BIGINT       NOT NULL,
    labels           VARCHAR(255) NULL COMMENT '逗号分隔：答错/该转人工/引用错/…',
    note             VARCHAR(1024) NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/TRIAGED/FIXED/DROPPED',
    created_by       VARCHAR(64)  NOT NULL DEFAULT 'anonymous',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_badcase_trace (trace_id),
    KEY idx_badcase_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '人工标记的坏例';
