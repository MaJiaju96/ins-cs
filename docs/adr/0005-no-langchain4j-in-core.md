# ADR-0005 不把 LangChain4J 引进内核

- 状态：已采纳
- 日期：2026-10-30
- 相关：`LlmPort`、`cs-core/pom.xml`

## 背景

项目目标之一是学 LangChain4J（也确实是简历上的加分项）。它提供 `ChatModel`、`ChatMemory`、`AiServices`、
工具调用、`ChatModelListener`（可用来对接 Langfuse 的 generation）。

诱惑是：直接用 `AiServices` 定义接口，几行代码就是一个「Agent」。

## 决策

1. **内核 `cs-core` 不依赖 LangChain4J。** 模型调用只认 `LlmPort`（`Request`/`Completion`）。
2. LangChain4J 在 `cs-bot` 适配层使用：实现 `LlmPort`、挂 `ChatModelListener` 做 trace 导出。
3. **不用它的 `ChatMemory`。** 记忆是 `turn` 表 + 按 `seq` 读最近 N 轮——我们已经有唯一事实源，不能再引入第二个。

## 理由

- `AiServices` 的价值是「快速得到一个能对话的对象」，代价是控制权交给框架：多一次隐式调用、多一段看不见的 prompt 模板、多一层难以断言的输出。
- 本项目的核心资产是「可测：每一轮的判断与执行都能被断言」。把编排权交给框架，等于把最重要的东西交出去。
- 但**工具调用、结构化输出、模型抽象**这些确实是 LangChain4J 做得好的地方，放在适配层用完全合理。

## 后果

- 好：内核可在没有任何模型的情况下单测（当前 `AskBackRouter` 就是「没有模型也能工作」的证明）。
- 好：换模型（DeepSeek ↔ 本地 vLLM ↔ 别的）只改适配层一个类。
- 坏：要自己写 OpenAI 兼容的 HTTP 调用与 JSON 解析（约 100 行）。
- 面试叙事：能用框架，也知道框架在哪里不该用——比「用了框架」更有说服力。
