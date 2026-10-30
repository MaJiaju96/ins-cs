/**
 * 与 cs-bot 的线格式一一对应（`ChatResponse` / `BlockView` / SSE 帧）。
 *
 * 这里手写一遍而不是从 Java 生成：换来的是「后端改字段 → 前端编译期报错」，
 * 而不是运行时静默丢字段。字段少、改得慢，手写的成本可以接受。
 */

export type RouteState = 'HIT_RULE' | 'HIT_MODEL' | 'ASK_BACK' | 'DEGRADED'

export interface RouteView {
  domain: string
  intent: string | null
  state: RouteState
  confidence: number
  ruleId: string | null
}

/** 六种 block，和 cs-core 的 sealed interface 一一对应。 */
export type BlockKind = 'text' | 'card' | 'suggestion' | 'thinking' | 'handoff' | 'hidden'

export interface BlockView {
  kind: BlockKind
  text?: string | null
  cardType?: string | null
  data?: Record<string, unknown> | null
  options?: string[] | null
  reason?: string | null
  summary?: string | null
  queue?: string | null
}

export interface UsageView {
  promptTokens: number
  completionTokens: number
}

export interface SpanView {
  type: string
  name: string
  durationMs: number
  status: string
  error?: string | null
}

/** `event: done` 的载荷。 */
export interface DonePayload {
  traceId: string
  seq: number
  usage: UsageView
  spans: SpanView[]
}
