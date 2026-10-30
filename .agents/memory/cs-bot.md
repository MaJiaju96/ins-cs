# 客服主链路（cs-bot + cs-core）

## 当前状态

- P0 骨架可跑：`mvn test` 16 绿，`java -jar` 起 8080，`POST /api/chat` 返回 route + blocks + spans。
- 分流靠**规则**：12 条内存规则，零模型调用（`AskBackRouter` 兜底恒反问）。没有 SSE、没有 Vue3 前端、没有落库。
- 知识库/订单/保单都是 `demo/DemoData` 里的常量，**没有一条 SQL 在跑**。
- `V1__core_schema.sql` / `V2__demo_seed.sql` 已写好但**未执行**（没有 datasource 配置就没人跑 Flyway）。
- 下一步是 P0 剩的三项：接真模型（P0-10）、Vue3 对话窗（P0-9）、落库（P1-1）。

## 流水

- 2026-10-30 建仓 + 三模块骨架；cs-core 定 IR 三层（TurnContext/RunTrace/Blocks）。@file:D:\Code\AiLearning\ins-cs\pom.xml
- 2026-10-30 分流四态与 `SimpleRouteMatcher`/`TriageService` 落地；模型不可用一律 `ASK_BACK`，不抛错。@file:cs-core/src/main/java/com/inscs/core/route/TriageService.java
- 2026-10-30 `ConversationService` 串起 triage→resolve→handle→落 trace；trace/事件是旁路，抛异常不影响主链路。@file:cs-core/src/main/java/com/inscs/core/ConversationService.java
- 2026-10-30 踩坑：JDK 17 的 switch 模式匹配是预览特性，全改 instanceof 链。@file:AGENTS.md
- 2026-10-30 `BrokerAgent` 四类 intent 落地（order.query/policy.surrender/claim.materials/handoff），全部走 DemoData。@file:cs-bot/src/main/java/com/inscs/bot/agent/BrokerAgent.java
- 2026-10-30 冒烟通过：`"2"` → menu.surrender/HIT_RULE，`"A1024"` → 订单 card，中文无乱码。@host:localhost:18080
- 2026-10-30 写 V1 八张表 + V2 虚构种子，未执行。@file:cs-bot/src/main/resources/db/migration/V1__core_schema.sql
