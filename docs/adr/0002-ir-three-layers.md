# ADR-0002 IR 按角色分三层：知道 / 做了 / 说了

- 状态：已采纳
- 日期：2026-10-30
- 相关：`cs-core/ir/*`、`docs/design-notes/reference-system.md` 巧思 2

## 背景

最初的设计按信息丰富度分 L0/L1/L2（基础 → 多层级 blocks/routing/handoff → span 树）。
问题：这三层的信息量其实是递进但**重叠**的，而它们的生命周期、落库位置、消费者完全不同。

考虑过两种切法：

- A. 按「前端能渲染什么」切 → 会让 IR 退化成 UI 的副产品，评测台读不到它要的东西。
- B. 按「记录的角色」切 → 输入侧 / 执行侧 / 输出侧。

## 决策

采纳 B，三层：

| 层 | 类型 | 生命周期 | 事实源 |
| --- | --- | --- | --- |
| 知道 | `TurnContext` | 单轮输入 | `turn` 表（入参与业务快照） |
| 做了 | `RunTrace`/`Span` | 单轮执行 | `span` 表 |
| 说了 | `Block`/`TurnResult` | 单轮输出 | `turn.blocks_json` |

配套规则：**每个字段必须能回答「支撑哪条指标」**。答不出来的字段不加。

## 后果

- 好：三层可独立演化与落库；重放时能分清「输入」与「过程」；评测台可以直接消费 `TurnResult` 而不依赖前端。
- 坏：`TurnResult` 同时持有 route + blocks + trace + usage，看起来是「大对象」；但它是**落库单位/回放单位/导出单位**，合并是有意的。
- 约束：`TurnContext` 不许携带运行期状态（那是 RunTrace 的事）；`Span` 不可变，由 `TraceRecorder` 收集。
