# ARCHITECTURE.md — ins-cs

## 1. 一句话

一轮对话 = **知道什么（TurnContext）→ 交给谁（RouteDecision）→ 做了什么（RunTrace）→ 说了什么（Block）**，
四件东西都有明确归属，任何一件缺失都会让「为什么这么答」不可追溯。

## 2. 分层

```
                      HTTP (ChatController, 线格式 BlockView/ChatResponse)
                                 │
                         ConversationService            ← 唯一知道全局的类（几十行）
                    ┌────────────┼────────────┬───────────────┐
              TriageService   SubAgentRegistry  TraceSink   EventPublisher
                    │             │              (port)       (port)
            RouteMatcher    SubAgent(plugin)          │            │
          (route_rule 表)   BrokerAgent…         MariaDB       RabbitMQ
                    │
              RouterPort（小模型兜底）
```

- `cs-core` 不认识 Spring、不认识数据库、不认识 Jackson。它只认识 **六个 port**：
  `LlmPort` / `RouterPort` / `RetrievalPort` / `TraceSink` / `EventPublisher` / `RouteRuleProvider`。
- `cs-bot` 是唯一适配层：把 port 换成实现，把领域类型换成线格式。
- `cs-lab` 是 IR 的**第二个消费者**。它的存在保证了 IR 字段不会退化成「只够前端渲染」。

## 3. IR 三层（按角色切，不按信息丰富度切）

| 层 | 类型 | 回答的问题 | 生命周期 | 事实源 |
| --- | --- | --- | --- | --- |
| 知道 | `TurnContext` | 进来时我们知道什么 | 单轮输入 | `turn` 表 + 上游 |
| 做了 | `RunTrace` / `Span` | 中间发生了什么、慢在哪、哪步降级 | 单轮执行 | `span` 表 |
| 说了 | `Block` / `TurnResult` | 输出给用户/坐席的是什么 | 单轮输出 | `turn.blocks_json` |

为什么不是按 L0/L1/L2（信息由少到多）切：三层的信息量接近，但**生命周期与事实源完全不同**。
按「传什么 / 记什么 / 说什么」切，每层都能独立演化、独立落库、独立做契约测试。

`RouteDecision` 横跨「知道」与「做了」：它是分流的结论，必须落 `turn` 表，
否则「路由命中率」「误问率」「降级率」三条指标全部不可测。

## 4. 分流：四态，三出口，规则优先

```java
RouteDecision.State = { HIT_RULE, HIT_MODEL, ASK_BACK, DEGRADED }
```

| 出口 | 触发 | 代价 | 记录 |
| --- | --- | --- | --- |
| `HIT_RULE` | `route_rule` 命中（priority 升序取第一条） | 0 次模型调用 | rule_id |
| `HIT_MODEL` | 规则未命中，`RouterPort` 返回且 `confidence >= 0.6` | 1 次往返 | confidence |
| `ASK_BACK` | 规则未命中 + 模型不可用/置信度不足 | 0 次往返，1 次交互 | state |
| `DEGRADED` | 命中但该域没有实现（`route.miss`）或执行抛错 | 0 次往返 | span=DEGRADED + 事件 |

三条铁律：

1. **规则命中不许碰模型。** 2 Mbps 上，省一次往返比省一次推理更值钱。
2. **模型不可用不许让请求失败。** `TriageService` 捕获异常 → 反问。分流是快路径，挂了就退化。
3. **低置信度不许硬分。** `< 0.6` 一律反问，并把候选编号下发（用户回「2」走 `EXACT` 规则，确定性分支）。

### 状态落库的字段

`turn.route_domain / route_intent / route_state / route_confidence / rule_id / fallback_msg_key`
+ `turn.degraded`。指标口径：

- 路由命中率 = `state='HIT_RULE'` 占比
- 误问率 = `state='ASK_BACK'` 且下一轮用户重述占比
- 降级率 = `degraded=1` 占比
- 规则覆盖率 = `rule_id IS NOT NULL` 占比

## 5. 一轮的完整路径（往返预算）

| 步骤 | 往返 | 说明 |
| --- | --- | --- |
| 加载规则 | 0（缓存 60s） | `RouteRuleProvider` 带缓存，刷新失败保留旧值 |
| 分流 | 0 或 1 | 规则命中 0 次；模型兜底 1 次 |
| 入口业务快照（订单/保单） | ≤1 | 只查一次，放进 `bizContext` 下发；域 Agent 不回查 |
| 域 Agent | 自管 | 工具调用计入该域自己的预算，不许嵌套回查 |
| trace 落库 | 异步 | 1 次批量写（turn 1 行 + span N 行），失败只记日志 |
| 事件 | 异步 | 失败只记日志 |

**硬上限：单请求 DB 往返 ≤3。** 超过就说明有人在域里偷偷回查。

## 6. 输出块（Block）：平级公民

`Text / Card / Suggestion / Thinking / Handoff / Hidden`

- `Card` 随流下发结构化数据 → 前端不用二次请求（省一个往返）
- `Suggestion` 把不确定性转成编号交互 → 用户回数字走确定性分支（省一次模型）
- `Handoff` 是给坐席的交接包（原因 + 已确认事实 + 技能组）→ 坐席不用重问
- `Hidden` 携带路由结论/引用/下一步动作 → 可观测性内嵌在协议里

`Block` 是领域类型；线格式是 `BlockView`（显式映射、空字段不输出）。领域可以动，线格式不许抖。

## 7. 扩展点：新增一个业务域

三处，且只有三处：

1. 实现 `SubAgent`（`domain()` / `canHandle()` / `handle()`），标 `@Component`
2. `route_rule` 加一行（Flyway 迁移 + review）
3. 如需新工具：实现一个方法 + 加一个 `Span.Type.TOOL` 记录

分流的坑已经填好：
- 域未实现 → `route.miss` 降级 + 事件，不抛异常
- 域执行抛错 → 用 `route_rule.fallback_msg_key` 指向的该域专属话术，不全局「系统繁忙」
- 未启用域 → 规则 `enabled=0` 留着，证明扩展口存在（延保/商家险）

## 8. 明确不做

| 不做 | 为什么 |
| --- | --- |
| 通用条件表达式引擎（MVEL/JsonPath/JSONata） | 一个人维护三套 DSL 是负债；复杂条件写实现类 |
| `TargetBridgeFactory` 式注册表工厂 | 只有一种目标类型（本地 Bean），抽象没有第二个实现验证 |
| 多模块 8 层拆分 | 一个人 + 一个前端，拆越细只增加跳转成本 |
| 外部 Memory 服务 | 记忆就是 `turn` 表 + 一次最近 N 轮查询，不需要独立服务 |
| trace 平台作为事实源 | 见 ADR-0004：评测要 GROUP BY/join/recall，且不能接受采样丢一半 |
| 把分流逻辑放进动态配置 | 见 ADR-0003：仓库查不到的分流，等于不可测试的分流 |

## 9. 决策索引

- ADR-0001 技术栈：Spring Boot 3.3 + JDK 17 + MyBatis-Plus（不用 JPA）
- ADR-0002 IR 按角色三层
- ADR-0003 分流规则事实源在仓库
- ADR-0004 trace 落 MariaDB，可观测平台旁路
- ADR-0005 不引 LangChain4J 进内核（P1 在适配层用）
