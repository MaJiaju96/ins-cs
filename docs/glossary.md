# 术语表

只收「容易理解错、且影响代码结构」的词。每个词给一句话定义 + 一个反例。

## 分流相关

**routing（路由）** — 由场域/渠道/配置决定的、请求进来就能确定的归属。
例：`channel=miniapp` → 走小程序话术集。反例：靠模型猜用户想问什么，那叫 classification。

**classification（分类）** — 由模型判断的、跑起来才知道的归属：意图、情绪、是否越权。
本项目里 `TriageService` 做的是 classification，`Domain` 的选择是 routing。**两者不能合成一个 if-else**。

**四态（RouteDecision.State）** — `HIT_RULE` / `HIT_MODEL` / `ASK_BACK` / `DEGRADED`。
不确定性是合法状态，不是异常。反例：`try { return model.classify() } catch { return BROKER }`——把不确定性压成默认值。

**反问（ASK_BACK）** — 一次低成本的澄清交互，通常带编号候选。用户回「2」走 `EXACT` 规则，确定性分支，不再过模型。

**误问率** — 反问后用户重述/放弃的比例。反问不是白拿的，问错了比猜错更让人烦。

**降级（DEGRADED）** — 命中但目标不可用（域未实现、超时、熔断），用该域专属话术收尾。

**降级链** — 一串**换假设**的尝试：向量召回 → 关键词重召回 → 条款定位 → 候选澄清 → 兜底拒答。
**retry ≠ fallback**：retry 是同一假设再试一次（弱网下是放大器），fallback 是换一个假设。

## IR 相关

**IR（中间表示）** — 本项目里就是三类对象：`TurnContext`（知道）/ `RunTrace`（做了）/ `Block`（说了）。
它不是「为了解耦而抽象」，是为了让评测台（第二个消费者）能读。

**TurnContext** — 一轮的输入侧全量：会话、序号、用户、历史、业务快照。可序列化，跨 Agent 传递。
**不携带**运行期状态（那是 RunTrace 的事）——混在一起会导致「重放时无法区分输入与过程」。

**Block** — 输出侧平级公民：`Text` / `Card` / `Suggestion` / `Thinking` / `Handoff` / `Hidden`。
不是「文本 + 附件」，是六种同等重要的消息类型。

**Span** — 一次可观测的动作：`ROUTE` / `MODEL` / `RETRIEVAL` / `TOOL` / `HANDOFF` / `RENDER`，
带 `status`（OK/ERROR/DEGRADED/SKIPPED）。降级也必须留 span，否则降级链会悄悄腐烂。

**seq** — 会话内权威序号（从 1 递增）。**排序只认 seq，不认时间戳**：多源合并时时间戳会打架，且时钟不可信。

**事实源** — 唯一权威数据。本项目是 MariaDB 的 `turn`/`span`。Langfuse 是旁路，允许丢。

**线格式（wire format）** — 对外 JSON 契约，由 `BlockView`/`ChatResponse` 显式映射。领域类型可演进，线格式不许抖。

## 工程约定

**契约（port）** — `cs-core` 里六个接口：`LlmPort` / `RouterPort` / `RetrievalPort` / `TraceSink` / `EventPublisher` / `RouteRuleProvider`。
内核只认这些，不认 Spring/DB/Jackson。

**往返预算** — 单请求允许的公网 DB 往返次数上限（≤3）。弱网上这是第一预算，比功能优先级高。

**卫星系统** — 挂在核心上的只读消费者（badcase、回放、评测、看板）。
它们只拿三样：数据（只读）、事件（MQ）、管理 API（标记/重放/导出）。**不许侵入主链路**。

**契约测试** — 对每个 `SubAgent` 的固定断言：必产至少一个 TOOL span、必给可用降级话术、不确定必给候选。

**样板房** — P0 的可演示切片：五个画面，刻意砍功能（只两级路由、只一个工具），目的是验证结构而不是堆功能。
