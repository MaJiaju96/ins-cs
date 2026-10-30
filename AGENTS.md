# AGENTS.md — ins-cs

给在本仓库工作的 AI 会话（以及三个月后的自己）的工作约定。**先读本文件，再读 `.agents/project/INDEX.md`。**

## 构建与验证

```bash
mvn -q test                                    # 全部测试（18 个，秒级）
mvn -q -pl cs-core test                        # 只跑内核
mvn -q -DskipTests package                     # 打包
java -jar cs-bot/target/cs-bot-0.1.0-SNAPSHOT.jar --server.port=18080

cd web && npm run typecheck && npm run build    # 前端（Node 16 可跑，版本已锁死，别升）
```

- JDK 17（不要用 21 语法：`switch` 模式匹配在 17 上是 preview，会让构建参数被污染——`BlockView` 里已踩过一次）
- 默认 profile 不连数据库、不调模型。要连库用 `--spring.profiles.active=dev`，配置文件模板见 `cs-bot/src/main/resources/application-dev.example.yml`
- 验证「有没有做完」的标准是 **`mvn test` 绿 + 手工 curl 一次真实响应**，不是「代码写完了」

## 硬规则

1. **内核零依赖。** `cs-core` 不许依赖 Spring / Jackson / 任何第三方运行时库。需要外部能力就加一个 `port` 接口。理由见 `docs/adr/0001`。
2. **往返预算。** 单请求 DB 往返 ≤3；不写 N+1；不 `SELECT *`；聚合留在 DB 侧；Redis 用 pipeline/Lua。改数据访问前先想清楚这会多几次公网往返（每次 ≈60ms）。
3. **规则与结构进仓库**（Flyway + review），**参数与开关进配置中心**（Nacos）。不要把分流逻辑挪进动态配置——见 `docs/adr/0003`。
4. **trace 只能落 MariaDB。** `turn` / `span` 是唯一事实源；外部 trace 平台是旁路、允许丢。见 `docs/adr/0004`。
5. **演示数据全部虚构。** 不出现真实公司名、真实产品条款、真实保单号。任何参考材料的原文（含内部系统名、包名）**不得进入本仓库**。
6. **凭据不进仓库。** 真实口令放 `.agents/local/`（已 gitignore）或用环境变量；提交前 `git status` 自查。
7. **不确定是合法状态。** 分层里任何一处「不知道」都应该变成一次反问或一次降级，而不是猜。不要用 try/catch 把不确定性压成默认值。

## 代码约定

- Java 17 + record + sealed interface；**不用 Lombok**（JDK 17 的 record 已经够，少一个注解处理器少一堆隐性行为）
- 注释写**为什么**，不写「设置 x 为 y」。关键取舍处必须留下代价说明
- 新增业务域 = 实现 `SubAgent` + 注册 Bean + `route_rule` 加一行。不要改分流器
- 单元测试用 AssertJ；每个测试名描述**行为**（`低置信度时改反问，而不是硬分到一个域`），不描述方法名
- 前端/线格式改动前，先看 `cs-bot/web/BlockView`：领域类型可以动，线格式不许抖
- 线格式（`BlockView`/`ChatResponse`）改动必须同步三处：`web/src/types.ts`、`BlockRenderer.vue`、`ChatStreamControllerTest` 的事件断言
- 前端 `npm` 依赖只增不改版本号；`web/package.json` 里没有 `^`，因为 Node 16 是硬约束

## 当前进度锚点

- 已完成：P0 骨架（内核 IR + 分流四态 + 经纪域 Agent（确定性）+ trace/事件桩 + HTTP 入口）+ SSE 流式 + Vue3 对话窗 + 18 测试
- 下一步：见 `docs/roadmap.md` 的 P0 剩余项（接真模型 P0-10、落库 P1-1、handoff 列表 P0-11）与 P1
- 做完任何一件事，在 `.agents/memory/` 追加一行流水，并更新「当前状态」
