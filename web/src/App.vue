<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import ChatWindow from './components/ChatWindow.vue'
import TraceDrawer from './components/TraceDrawer.vue'
import { useChat, type TurnMessage } from './composables/useChat'

const { messages, busy, send, reset } = useChat()

/** 组件是逐个注册的（见 main.ts），所以语言包要在这里显式给。 */
const locale = zhCn

const drawerOpen = ref(false)
const inspected = ref<TurnMessage | null>(null)
const health = ref<'checking' | 'up' | 'down'>('checking')

const HEALTH_TEXT: Record<'checking' | 'up' | 'down', string> = {
  checking: '检查中',
  up: '后端在线',
  down: '后端离线',
}

/** 最近一条带 trace 的消息：顶部「看 trace」按它来。 */
const lastTraced = computed<TurnMessage | null>(
  () => [...messages.value].reverse().find((m) => m.traceId !== null) ?? null,
)

function openTrace(message: TurnMessage): void {
  inspected.value = message
  drawerOpen.value = true
}

onMounted(async () => {
  try {
    const resp = await fetch('/api/chat/health')
    health.value = resp.ok ? 'up' : 'down'
  } catch {
    health.value = 'down'
  }
})
</script>

<template>
  <el-config-provider :locale="locale">
    <div class="app-shell">
      <header class="app-header">
        <div class="brand">
          <div class="brand-mark">in</div>
          <div>
            <div class="brand-text">安心出行意外险 · 客服</div>
            <div class="brand-sub">P0 样板房 · 规则分流 · 数据全部虚构</div>
          </div>
        </div>

        <div class="header-actions">
          <span class="status">
            <i class="status-dot" :class="health" />
            {{ HEALTH_TEXT[health] }}
          </span>
          <el-button
            size="small"
            :disabled="!lastTraced"
            @click="lastTraced && openTrace(lastTraced)"
          >
            看 trace
          </el-button>
          <el-button size="small" plain @click="reset">新会话</el-button>
        </div>
      </header>

      <main class="app-main">
        <ChatWindow :messages="messages" :busy="busy" @send="send" @trace="openTrace" />
      </main>

      <TraceDrawer v-model="drawerOpen" :message="inspected" />
    </div>
  </el-config-provider>
</template>
