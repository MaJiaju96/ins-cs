# REQUIREMENTS.md — 需求与验收标准

勾选状态以仓库实际为准。**验收标准写成可执行的断言**，不写「体验良好」。

## P0 · 样板房（当前阶段）

| # | 需求 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| P0-1 | 内核 IR 成型 | `TurnContext`/`RouteDecision`/`Block`/`Span`/`RunTrace`/`TurnResult` 全在 `cs-core`，且 `cs-core` **零运行时依赖**（`mvn dependency:tree` 只剩 junit/assertj 在 test scope） | ✅ |
| P0-2 | 分流四态 | 规则命中 / 模型命中 / 反问 / 降级 四条路径各有单测；规则命中时模型调用次数 = 0 | ✅ |
| P0-3 | 一条链跑通 | `curl /api/chat` 发「我要退保」「A1024」「你好呀」三种输入，均返回 200 且 `blocks` 非空 | ✅ |
| P0-4 | trace 可见 | 响应里带 `route`（含 state/ruleId）与 `spans`；日志一行一轮 | ✅ |
| P0-5 | 降级不失败 | 域未实现 → `route.miss` 事件 + 专属话术；域抛错 → 该域 `fallback_msg_key` 话术；两者都返回 200 | ✅ |
| P0-6 | 线格式隔离 | `BlockView` 显式映射，未知 Block 类型直接抛异常（而不是静默丢字段） | ✅ |
| P0-7 | 规则集 | 10 条经纪域规则（含 3 条数字菜单）+ 2 条 disabled 的延保/商家险规则 | ✅ |
| P0-8 | 库表设计 | `V1__core_schema.sql` 8 张表 + `V2__demo_seed.sql` 虚构种子 | ✅（未在真实库执行） |
| P0-9 | 前端对话窗 | Vue3 单页：输入框 + 流式气泡 + 卡片渲染 + 选项按钮 + 「查看 trace」抽屉 | ⬜ |
| P0-10 | 接真模型 | `LlmPort` 实现（OpenAI 兼容）+ 退保条款问答走模型，仍保留确定性兜底 | ⬜ |
| P0-11 | 转人工待处理列表 | handoff 落表 + 极简列表页（不做坐席工作台） | ⬜ |

**P0 出口标准**：能录一段 60 秒演示：问退保 → 看到答案带引用 → 点「帮我试算」→ 看到卡片 → 点「转人工」→ 待处理列表出现交接包 → 打开 trace 看到 span 树。

## P1 · 地基（3–4 周）

| # | 需求 | 验收标准 |
| --- | --- | --- |
| P1-1 | 接 MariaDB | dev profile 起得来；`turn`/`span`/`rule` 落库；一次请求 DB 往返 ≤3（用日志/计数器证明） |
| P1-2 | 规则外置到表 | `RouteRuleProvider` 换成 DB 实现 + 60s 缓存；刷新失败保留旧值 |
| P1-3 | 记忆 | 最近 N 轮从 `turn` 表按 `seq` 读，**按 seq 排序不按时间戳**；上限 10 轮 |
| P1-4 | 三级路由 | entry → `ins.triage` → broker/extwarranty/merchant；未实现域走 `route.miss` |
| P1-5 | 工具四件 | `order.query` / `policy.query` / `refund.quote` / `surrender.quote`，各带幂等键与超时 |
| P1-6 | 检索 | `RetrievalPort` 落地（本地向量 + 关键词双路），三态阈值可配置（0.8/0.3） |
| P1-7 | 合规话术 | 规则表 + 最小合规检查（不得承诺收益、必须给免责引导）；不合规 → 拦截 + 记录 |
| P1-8 | 交接包 | handoff 落表（原因/已确认事实/技能组/最近 3 轮摘要） |
| P1-9 | Langfuse 旁路 | 异步导出 generation/span；**失败不影响主链路**；MariaDB 仍是唯一事实源 |
| P1-10 | 契约测试 | 每个 SubAgent 一组契约测试：必产至少一个 TOOL span、必给可用降级话术、不确定必给候选 |

## P2 · 卫星系统（每个 2–3 周）

| # | 卫星 | 验收标准 |
| --- | --- | --- |
| P2-1 | badcase 收集箱 | 订阅 `cs.turn.completed` / `cs.badcase.marked`；列表 + 打标；导出 JSONL |
| P2-2 | 轨迹回放 | 输入 traceId → 还原成「用户问了什么/系统怎么想/调了什么工具/慢在哪」时间轴 |
| P2-3 | 评测台 | 黄金集 ≥60 条；L1 确定性断言 + L2 检索断言（recall@k / forbidden chunk）+ L3 LLM 裁判（三次投票 + 一致率）；run diff |
| P2-4 | 运营配置台 | 规则/话术/prompt 版本管理，改动走「草稿 → 对比 → 发布」，发布内容落 git 可追溯 |
| P2-5 | 成本看板 | 每轮 token/耗时/模型分布；按域、按规则命中与否分组 |
| P2-6 | 话术质检 | 抽样 + LLM 打分（服务态度/准确性/合规），产出周报 |

**顺序不可颠倒**：badcase 收集箱最先（2–3 天就能用），因为评测集是从坏例里长出来的，不是先编黄金集再找理由。

## 非功能要求

- 单请求 P95 < 2s（不含模型流式首 token）
- 单请求 DB 往返 ≤3；`SELECT *` 零容忍
- 模型调用失败率上升时，`ASK_BACK` 占比应上升而非 5xx 上升
- 所有降级路径必须留下 span（`DEGRADED`），否则视为 bug
- 数据全虚构；无凭据入库
