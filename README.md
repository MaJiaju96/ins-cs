# ins-cs

AI 保险客服机器人 + 评测台。一个可以长期长的个人项目：客服系统负责「跑起来」，评测台负责「证明它是对的」。

> 这不是 demo 拼盘。整套设计围绕一个具体矛盾：**在一条 2 Mbps、RTT 40–80 ms 的公网链路上，怎么让一个多层 Agent 的客服系统既跑得动、又说得清。**

## 为什么值得做

大多数客服机器人的问题是：上线之后没人说得清「上周那 200 个会话为什么错」。

所以这个项目的第一性原则是 **trace 必须落库、必须能回放**：

- 每一轮对话 = 1 行 `turn` + N 行 `span`，落 MariaDB，是唯一事实源；
- 分流判定（规则命中 / 模型命中 / 反问 / 降级）四态全部记录，于是「路由命中率」「误问率」「降级率」都可测；
- 外部可观测平台（Langfuse）只是旁路导出、允许丢——因为评测要 `GROUP BY`、要 join、要算 recall，那不是 trace 平台该干的活；
- 记下来之后，评测台可以对任意一轮做重放与 diff：改了一条规则/一段 prompt，到底修好了还是改坏了。

## 当前状态

`P0 骨架 + 对话界面`：能跑通「分流 → 域 Agent → 文本/卡片/追问/交接包 + 可见 trace」整条链，**默认不连任何中间件、不调任何模型**。前端是 Vue3 单页，用 SSE 逐块渲染（先到 `route`，再到 `block`，最后 `done`）。

```bash
# 需要 JDK 17 + Maven
mvn -q test                 # 18 个测试
mvn -q -DskipTests package
java -jar cs-bot/target/cs-bot-0.1.0-SNAPSHOT.jar

curl -s -X POST http://127.0.0.1:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"c-1","seq":1,"message":"我要退保"}'
```

返回里同时有：正文、编号候选、隐藏参数（引用哪条规则）、`route` 判定、`spans` 执行轨迹。

流式接口 `POST /api/chat/stream` 出四类事件：`route` / `block` / `done` / `error`。前端跑起来：

```bash
cd web && npm install && npm run dev   # http://localhost:5173，/api 已代理到 8080
```

## 结构

```
cs-core/   内核。零运行时依赖（不依赖 Spring / Jackson），只有契约与 IR
cs-bot/    客服服务。Spring Boot 适配层 + HTTP 入口（同步 /api/chat、流式 /api/chat/stream）
web/       对话界面。Vue3 + Vite + TS + Element Plus，手写 SSE 解析（不用 EventSource，因为要 POST）
cs-lab/    评测台。P2 才有肉，现在是占位——但它的存在决定了 IR 的字段设计
docs/      ADR、设计笔记、术语表、路线图
.agents/   给下一个会话的自己看的项目上下文（project / memory），local/ 不提交
```

内核的五个契约（`cs-core/src/main/java/com/inscs/core/port/`）：
`LlmPort`（模型）、`RouterPort`（分流模型）、`RetrievalPort`（检索）、`TraceSink`（落库）、`EventPublisher`（事件）。
换实现只改 `cs-bot/config`，内核不动——这是「内核零依赖」换来的东西。

## 三条硬约束

1. **往返预算优先于功能。** 规则命中不调模型（0 次往返）；模型兜底 1 次；单请求 DB 往返 ≤3；禁 `SELECT *`。
2. **规则与结构进仓库，参数与开关进配置中心。** 分流规则是 Flyway 迁移 + code review 的一部分，不是某个配置中心里别人昨晚改的一个值——否则「线上为什么这么分流」永远查不到。
3. **演示数据全部虚构。** 不出现任何真实公司名、真实产品、真实条款。这是个人项目的边界，也是能公开的前提。

## 文档

- [`docs/roadmap.md`](docs/roadmap.md) — P0/P1/P2 三阶段与验收标准
- [`docs/design-notes/reference-system.md`](docs/design-notes/reference-system.md) — 参考系统拆解：哪些巧思值得学、哪些是负债
- [`docs/glossary.md`](docs/glossary.md) — 术语（routing vs classification、block、span、降级链…）
- [`docs/adr/`](docs/adr/) — 技术决策记录
- [`AGENTS.md`](AGENTS.md) — 给 AI/新会话的工作约定
