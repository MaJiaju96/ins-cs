# ins-cs-web

`cs-bot` 的对话窗。Vue 3 + Vite + TypeScript + Element Plus。

它的职责只有一个：**把一轮对话的 `blocks` 如实渲染出来**，并把这一轮发生了什么摆在用户面前
（分流徽标、trace id、span 时间线）。

## 跑起来

后端先起（另一个终端）：

```bash
cd ..
mvn -q -DskipTests package
java -jar cs-bot/target/cs-bot-0.1.0-SNAPSHOT.jar
```

前端：

```bash
npm install
npm run dev        # http://localhost:5173，/api 走 vite proxy 到 127.0.0.1:8080
```

其它命令：

```bash
npm run typecheck  # vue-tsc --noEmit
npm run build      # 类型检查 + 产物到 dist/
npm run preview    # 预览 dist
```

## 为什么是 fetch 而不是 EventSource

`EventSource` 只能 GET，而对话必须 POST 消息体。所以 `src/api/chat.ts` 用
`fetch` + `ReadableStream` 自己解 SSE 帧——协议不变（`event:` / `data:` / 空行），只是换个读法。

这也是为什么后端只定义了 4 个事件：`route` / `block` / `done` / `error`。
事件越少，两边越不容易对不上。

## 结构

```
src/
  api/chat.ts            SSE over fetch，帧解析与 4 个事件
  composables/useChat.ts 一轮的状态机（拼消息、管 seq、增量 push block）
  types.ts               线格式的 TS 镜像（后端改字段 → 这里编译期报错）
  components/
    ChatWindow.vue       空态 + 消息列表 + 自动滚底
    MessageBubble.vue    用户气泡 / 助手卡片 / trace 脚注
    BlockRenderer.vue    六种 block 的渲染（hidden 不上屏）
    RouteBadge.vue       四态 + 域 + 规则 id
    Composer.vue         输入框与快捷问题
    TraceDrawer.vue      分流、span 时间线、用量、原始 JSON
```

## 已知的取舍

- **没有登录、没有多会话列表**：P0 是样板房，会话 id 存在 localStorage，刷新还在，清掉就没了。
- **卡片字段用开放 Map**：后端不为了渲染改结构，前端「认识的翻译、不认识的照原样显示」。
- **Node 16 环境**：`element-plus` 锁在 2.5.6、`vite` 锁在 4.5.14。升级到 Node 18+ 后才能放开。
