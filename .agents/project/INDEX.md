# 项目上下文索引（ins-cs）

status: ready
last_verified: 2026-10-30
commit: —（初始 commit 前生成，首次提交后更新这里）

## 这是什么

AI 保险客服机器人 + 评测台，个人长期项目。P0 骨架已可运行（`mvn test` 绿、`/api/chat` 可 curl）。

## 按任务读

| 你要做什么 | 先读 | 再读 |
| --- | --- | --- |
| 改代码 / 加功能 | `PROJECT.md`（结构+约定） | `AGENTS.md`（硬规则）、对应 ADR |
| 改分流 / 路由 / 规则 | `ARCHITECTURE.md` §分流 | `docs/adr/0003`、`cs-core/route/*` |
| 改 IR / trace / 落库 | `ARCHITECTURE.md` §IR 三层 | `docs/adr/0002`、`docs/adr/0004` |
| 做评测台 / 卫星系统 | `REQUIREMENTS.md` P2 | `docs/roadmap.md` |
| 想接模型 / 检索 | `ARCHITECTURE.md` §契约 | `docs/roadmap.md` P0 剩余项 |
| 不懂某个词 | `docs/glossary.md` | — |

## 文件清单

| 文件 | 内容 | 何时过期 |
| --- | --- | --- |
| `PROJECT.md` | 目录结构、构建、模块职责、依赖、代码约定 | 加模块/换依赖时 |
| `ARCHITECTURE.md` | 分层、IR 三层、分流四态、五个契约、扩展点、明确不做的 | 架构变更时 |
| `PRODUCT.md` | 目标用户、场景、价值、范围外 | 目标变化时 |
| `REQUIREMENTS.md` | P0/P1/P2 需求与验收标准（可勾选） | 每阶段结束 |
| `docs/roadmap.md` | 时间线、阶段目标、验收门槛 | 阶段推进时 |
| `docs/design-notes/reference-system.md` | 参考系统的巧思与负债拆解（已匿名化） | 不再常读，作为背景 |
| `docs/glossary.md` | 术语表 | 新增术语时 |
| `docs/adr/000X-*.md` | 已定型的决策与理由 | 只增不改（要推翻就新写一条） |
| `AGENTS.md`（根） | 构建命令 + 硬规则 | 约定变化时 |
| `.agents/memory/` | 当前状态 + 流水 | 每次有产出后追加 |
| `.agents/local/` | 本机真实连接参数（**不提交**） | 换环境时 |

## 一句话记住的三件事

1. 内核 `cs-core` 零依赖，认得五个契约，不认识 Spring。
2. 每一轮都落 `turn` + `span`，分流四态必须落库，否则一切都不可测。
3. 2 Mbps 上，往返次数是第一预算：规则命中就该 0 次模型调用。
