import { computed, reactive, ref } from 'vue'
import { streamTurn } from '../api/chat'
import type { BlockView, DonePayload, RouteView, SpanView, UsageView } from '../types'

export interface TurnMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
  blocks: BlockView[]
  route: RouteView | null
  spans: SpanView[]
  usage: UsageView | null
  traceId: string | null
  seq: number
  streaming: boolean
  error: string | null
}

const STORAGE_KEY = 'ins-cs.conversationId'

function loadConversationId(): string {
  try {
    return localStorage.getItem(STORAGE_KEY) ?? ''
  } catch {
    return ''
  }
}

/**
 * 一轮对话的本地状态机。
 *
 * <p>关键点：助手消息对象是 `reactive` 的，push 进数组后仍然持有它自己的代理——
 * 这样 SSE 回调里直接往 `assistant.blocks` 里 push，界面就会增量更新，
 * 不需要「收完再一次性替换」那种假流式。
 */
export function useChat() {
  const messages = ref<TurnMessage[]>([])
  const conversationId = ref(loadConversationId())
  const seq = ref(0)
  const busy = ref(false)

  /** 最近一条已完成的助手消息：Trace 抽屉默认看它。 */
  const lastAssistant = computed<TurnMessage | null>(
    () => [...messages.value].reverse().find((m) => m.role === 'assistant' && !m.streaming) ?? null,
  )

  function send(text: string): void {
    const content = text.trim()
    if (!content || busy.value) return

    messages.value.push(userMessage(content))

    const assistant = reactive<TurnMessage>(blankAssistant())
    messages.value.push(assistant)

    busy.value = true
    seq.value += 1
    const turnSeq = seq.value

    streamTurn(
      {
        conversationId: conversationId.value || undefined,
        seq: turnSeq,
        channel: 'web',
        message: content,
      },
      {
        onRoute: (route) => {
          assistant.route = route
        },
        onBlock: (block) => {
          assistant.blocks.push(block)
        },
        onDone: (payload: DonePayload) => {
          assistant.traceId = payload.traceId
          assistant.spans = payload.spans
          assistant.usage = payload.usage
          // 首轮服务端会派生一个 conversationId，把它记下来，后续轮次才接得上
          if (!conversationId.value) {
            conversationId.value = `c-${payload.traceId}`
            persistConversationId(conversationId.value)
          }
        },
        onError: (message) => {
          assistant.error = message
        },
      },
    )
      .catch((e: unknown) => {
        assistant.error = `请求失败：${(e as Error).message}`
      })
      .finally(() => {
        assistant.streaming = false
        busy.value = false
      })
  }

  function reset(): void {
    messages.value = []
    seq.value = 0
    conversationId.value = ''
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      // 隐私模式下 localStorage 可能不可用，忽略
    }
  }

  return { messages, conversationId, busy, lastAssistant, send, reset }
}

function userMessage(text: string): TurnMessage {
  return {
    id: newId(),
    role: 'user',
    text,
    blocks: [],
    route: null,
    spans: [],
    usage: null,
    traceId: null,
    seq: -1,
    streaming: false,
    error: null,
  }
}

function blankAssistant(): TurnMessage {
  return {
    id: newId(),
    role: 'assistant',
    text: '',
    blocks: [],
    route: null,
    spans: [],
    usage: null,
    traceId: null,
    seq: -1,
    streaming: true,
    error: null,
  }
}

function persistConversationId(id: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, id)
  } catch {
    // 同上
  }
}

let counter = 0
function newId(): string {
  counter += 1
  return `${Date.now().toString(36)}-${counter}`
}
