import type { BlockView, DonePayload, RouteView } from '../types'

export interface TurnRequest {
  /** 第一轮可以为空：后端会用 traceId 派生一个，再由前端记进 localStorage。 */
  conversationId?: string
  seq: number
  userId?: string
  channel?: string
  message: string
}

export interface TurnHandlers {
  onRoute?: (route: RouteView) => void
  onBlock?: (block: BlockView) => void
  onDone?: (payload: DonePayload) => void
  onError?: (message: string) => void
}

/**
 * 一轮对话，用 POST + SSE 读回来。
 *
 * <p>为什么不用 `EventSource`：它只能 GET，消息体得塞进 URL，长文本会撞长度限制，
 * 也不方便带 userId/channel。这里自己解 SSE 帧——协议本身只有三行：
 * `event:`、`data:`、空行分隔。
 *
 * <p>断流与冒错都不抛给调用方，一律走 `onError`：调用方拿到的永远是一个「有始有终」的回调序列。
 */
export async function streamTurn(
  req: TurnRequest,
  handlers: TurnHandlers,
  signal?: AbortSignal,
): Promise<void> {
  let resp: Response
  try {
    resp = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(req),
      signal,
    })
  } catch (e) {
    if (isAbort(e)) return
    handlers.onError?.(`连不上后端：${(e as Error).message}`)
    return
  }

  if (!resp.ok || !resp.body) {
    handlers.onError?.(`后端返回 ${resp.status}`)
    return
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // 帧以空行结束；\r\n 与 \n 都要吃得下（中间可能隔着代理）
      const frames = buffer.split(/\r?\n\r?\n/)
      buffer = frames.pop() ?? ''
      for (const frame of frames) dispatch(frame, handlers)
    }
    // 服务端若没发末尾空行，最后一帧还留在 buffer 里
    if (buffer.trim().length > 0) dispatch(buffer, handlers)
  } catch (e) {
    if (!isAbort(e)) handlers.onError?.(`读流中断：${(e as Error).message}`)
  } finally {
    reader.releaseLock()
  }
}

function dispatch(frame: string, handlers: TurnHandlers): void {
  let event = 'message'
  const data: string[] = []

  for (const raw of frame.split(/\r?\n/)) {
    const line = raw.trimEnd()
    if (line.length === 0 || line.startsWith(':')) continue // 注释 / 心跳
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) {
      // 规范里冒号后可有一个空格，要去掉
      data.push(line.slice(5).replace(/^ /, ''))
    }
  }

  if (data.length === 0) return
  const raw = data.join('\n')

  try {
    switch (event) {
      case 'route':
        handlers.onRoute?.(JSON.parse(raw) as RouteView)
        break
      case 'block':
        handlers.onBlock?.(JSON.parse(raw) as BlockView)
        break
      case 'done':
        handlers.onDone?.(JSON.parse(raw) as DonePayload)
        break
      case 'error': {
        const payload = JSON.parse(raw) as { message?: string }
        handlers.onError?.(payload.message ?? '未知错误')
        break
      }
      default:
        break
    }
  } catch {
    handlers.onError?.(`无法解析的帧：${raw.slice(0, 120)}`)
  }
}

function isAbort(e: unknown): boolean {
  return e instanceof DOMException && e.name === 'AbortError'
}
