# PROJECT.md — 结构、构建、约定

## 模块

| 模块 | 职责 | 依赖 |
| --- | --- | --- |
| `cs-core` | IR（TurnContext/RunTrace/Block/TurnResult/RouteDecision/Span）、分流（RouteRule/RouteMatcher/TriageService）、编排（ConversationService）、五个 port 契约、TraceRecorder、降级话术 | **零运行时依赖**（仅测试期 junit+assertj） |
| `cs-bot` | Spring Boot 适配：把 port 换成实现（P0 是桩）、唯一 HTTP 入口 `/api/chat`、线格式 `BlockView`/`ChatResponse` | cs-core + spring-boot-starter-web/validation |
| `cs-lab` | 评测台。P2 才有肉，现为占位 + `/health` | cs-core + starter-web |

父 POM：`spring-boot-starter-parent 3.3.5`，`java.version=17`，`groupId=com.inscs`，`version=0.1.0-SNAPSHOT`。

## 目录

```
cs-core/src/main/java/com/inscs/core/
├── ConversationService.java      编排：分流→找 Agent→执行→补 trace→旁路落库
├── agent/                        SubAgent（扩展点）、SubAgentRegistry
├── ir/                           Domain、TurnContext、RouteDecision、Block、Span、RunTrace、TurnResult、RetrievalHit
├── port/                         LlmPort、RouterPort、RetrievalPort、TraceSink、EventPublisher、RouteRuleProvider
├── retrieval/                    ConfidencePolicy（0.8/0.3 三态阈值）
├── route/                        RouteRule、RouteMatcher、SimpleRouteMatcher、TriageService
├── text/                         FallbackMessages（降级话术，按 key 引用）
└── trace/                        TraceRecorder（span 收集器）

cs-bot/src/main/java/com/inscs/bot/
├── InsCsBotApplication.java
├── config/                       CoreConfig（内核 Bean）、BotConfig（P0 桩装配 + SubAgent 自动注册）
├── agent/BrokerAgent.java        经纪域 Agent（P0 确定性分支）
├── demo/DemoData.java            演示数据（全虚构）
├── stub/                         AskBackRouter、InMemoryRouteRuleProvider、LoggingTraceSink、LoggingEventPublisher
└── web/                          ChatController、ChatRequest、ChatResponse、BlockView

cs-bot/src/main/resources/
├── application.yml               默认 profile：不连库、不调模型
├── application-dev.example.yml   dev profile 模板（Hikari 8、Flyway、Redis、LLM 端点）
└── db/migration/                 V1__core_schema.sql（8 张表）、V2__demo_seed.sql（规则+虚构数据）
```

## 构建

```bash
mvn -q test                                    # 16 个测试
mvn -q -pl cs-core test
mvn -q -DskipTests package
java -jar cs-bot/target/cs-bot-0.1.0-SNAPSHOT.jar --server.port=18080
```

默认 profile 裸启动、不依赖任何中间件；`--spring.profiles.active=dev` 才连 MariaDB/Redis。

## 依赖清单（当前）

- 运行时：Spring Boot 3.3.5（web、validation）
- 测试：junit-jupiter、assertj、spring-boot-starter-test
- **未引入**：MyBatis-Plus / Flyway / mariadb-java-client（已在 `cs-bot/pom.xml` 注释块列出，P1 放开）、Lombok（不用）、任何 LangChain4J（见 ADR-0005）
- 计划引入：`mybatis-plus-spring-boot3-starter 3.5.7`、`flyway-core`+`flyway-mysql`、`mariadb-java-client`、LangChain4J（仅 cs-bot 适配层用）

## 代码约定

- record 优先；不写 getter/setter 样板；**不用 Lombok**
- sealed interface 表达「有限分支」（Block/Span.Type）；`instanceof` 链而非 `switch` 模式匹配（JDK 17）
- 领域类型与线格式分离：`Block`（领域）→ `BlockView`（线格式）
- 异常用在 IO/参数错；业务不确定性用「状态」（`RouteDecision.State`、`Span.Status`）
- 注释写为什么，关键取舍要写清代价
- 测试名描述行为，断言用 AssertJ，测试不依赖执行顺序

## 已知坑

- JDK 17 不认 `switch` 模式匹配，会要求 `--enable-preview`。已在 `BlockView` 踩过，保持 `instanceof` 链
- Maven 输出中文在 Windows 控制台是乱码，但 HTTP 响应体是正确 UTF-8（已在 `pom.xml` 设置 `-Dfile.encoding=UTF-8`）。**看到乱码先别改代码**
- `Domain.code()` 返回小写（`broker`），线格式与表里都用小写；断言别写大写
