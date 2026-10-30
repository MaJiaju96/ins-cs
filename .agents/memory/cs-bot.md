# 客服主链路（cs-bot + cs-core）

## 当前状态

- 前后端都能跑：`mvn test` 18 绿；`java -jar` 起 8080；`POST /api/chat`（同步）与 `POST /api/chat/stream`（SSE 四事件 route/block/done/error）走同一段编排。
- 前端 `web/` 是 Vue3+Vite+TS+Element Plus 单页：流式气泡、卡片、选项按钮、trace 抽屉。**未经真实浏览器实测**，只验证了构建产物与事件契约。
- 分流靠**规则**：12 条内存规则，零模型调用（`AskBackRouter` 兜底恒反问）。知识/订单/保单仍是 `DemoData` 常量，**没有一条 SQL 在跑**。
- `V1__core_schema.sql` / `V2__demo_seed.sql` 已写好但**未执行**（无 datasource 就不跑 Flyway）。
- 下一步：接真模型（P0-10）→ 落库（P1-1）→ handoff 待处理列表（P0-11）。

## 流水

- 2026-10-30 建仓 + 三模块骨架；cs-core 定 IR 三层（TurnContext/RunTrace/Blocks）。@file:D:\Code\AiLearning\ins-cs\pom.xml
- 2026-10-30 分流四态与 `SimpleRouteMatcher`/`TriageService` 落地；模型不可用一律 `ASK_BACK`，不抛错。@file:cs-core/src/main/java/com/inscs/core/route/TriageService.java
- 2026-10-30 `ConversationService` 串起 triage→resolve→handle→落 trace；trace/事件是旁路，抛异常不影响主链路。@file:cs-core/src/main/java/com/inscs/core/ConversationService.java
- 2026-10-30 踩坑：JDK 17 的 switch 模式匹配是预览特性，全改 instanceof 链。@file:AGENTS.md
- 2026-10-30 `BrokerAgent` 四类 intent 落地（order.query/policy.surrender/claim.materials/handoff），全部走 DemoData。@file:cs-bot/src/main/java/com/inscs/bot/agent/BrokerAgent.java
- 2026-10-30 冒烟通过：`"2"` → menu.surrender/HIT_RULE，`"A1024"` → 订单 card，中文无乱码。@host:localhost:18080
- 2026-10-30 写 V1 八张表 + V2 虚构种子，未执行。@file:cs-bot/src/main/resources/db/migration/V1__core_schema.sql
- 2026-10-30 推上 GitHub（ad10cb9，68 文件，公开仓库）。@host:github.com/MaJiaju96/ins-cs
- 2026-10-30 加 `TurnObserver` 让编排可观察；`/api/chat/stream` 用 SSE 推 route/block/done，`ConversationService.finish()` 是唯一出口，四条降级路径都走它。@file:cs-core/src/main/java/com/inscs/core/port/TurnObserver.java
- 2026-10-30 前端 Node 16 装 element-plus 2.14.7 拖进要求 Node≥22 的 @vueuse/core → 全部精确锁版本重装，落 2.5.6。@file:web/package.json
- 2026-10-30 前端体积：整包 CSS 320KB → 按需引入后 53KB（gzip 8.6），JS 874KB → 157KB（gzip 59）。@file:web/src/main.ts
- 2026-10-30 加 SSE 契约测试（事件顺序 route<block<done；降级不出 error 帧），全量 18 绿。@file:cs-bot/src/test/java/com/inscs/bot/web/ChatStreamControllerTest.java
