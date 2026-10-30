<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TurnMessage } from '../composables/useChat'

const props = defineProps<{ modelValue: boolean; message: TurnMessage | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const showRaw = ref(false)

const route = computed(() => props.message?.route ?? null)
const spans = computed(() => props.message?.spans ?? [])
const usage = computed(() => props.message?.usage ?? null)
const traceId = computed(() => props.message?.traceId ?? '')
const totalMs = computed(() => spans.value.reduce((sum, span) => sum + span.durationMs, 0))

const raw = computed(() =>
  JSON.stringify(
    { traceId: traceId.value, route: route.value, usage: usage.value, spans: spans.value },
    null,
    2,
  ),
)

const STATUS_TYPE: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
  OK: 'success',
  DEGRADED: 'warning',
  ERROR: 'danger',
  SKIPPED: 'info',
}

function close(value: boolean): void {
  emit('update:modelValue', value)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="460px"
    title="这一轮发生了什么"
    @update:model-value="close"
  >
    <template v-if="message">
      <div class="trace-block">
        <div class="trace-label">trace id</div>
        <div class="trace-mono">{{ traceId || '—' }}</div>
      </div>

      <div v-if="route" class="trace-block">
        <div class="trace-label">分流</div>
        <div class="trace-mono">
          {{ route.state }} · {{ route.domain }}
          <template v-if="route.intent"> · {{ route.intent }}</template>
          <template v-if="route.ruleId"> · #{{ route.ruleId }}</template>
          · conf {{ route.confidence.toFixed(2) }}
        </div>
        <div class="trace-label" style="margin-top: 6px">
          四态里只有 HIT_RULE 是零模型调用；ASK_BACK 是它在反问你而不是在装懂。
        </div>
      </div>

      <div class="trace-block">
        <div class="trace-label">时间线（合计 {{ totalMs }} ms）</div>
        <table class="trace-table">
          <thead>
            <tr>
              <th>类型</th>
              <th>名称</th>
              <th>耗时</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(span, index) in spans" :key="index">
              <td class="mono">{{ span.type }}</td>
              <td>{{ span.name }}</td>
              <td class="mono">{{ span.durationMs }} ms</td>
              <td>
                <el-tag size="small" effect="plain" :type="STATUS_TYPE[span.status] ?? 'info'">
                  {{ span.status }}
                </el-tag>
              </td>
            </tr>
            <tr v-if="spans.length === 0">
              <td colspan="4" style="color: var(--text-dim)">没有 span</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="usage" class="trace-block">
        <div class="trace-label">用量</div>
        <div class="trace-mono">
          prompt {{ usage.promptTokens }} / completion {{ usage.completionTokens }}
          <template v-if="usage.promptTokens + usage.completionTokens === 0">
            （本轮未调用模型）
          </template>
        </div>
      </div>

      <div class="trace-block">
        <el-button size="small" text @click="showRaw = !showRaw">
          {{ showRaw ? '收起原始 JSON' : '看原始 JSON' }}
        </el-button>
        <pre v-if="showRaw" class="trace-pre">{{ raw }}</pre>
      </div>
    </template>

    <el-empty v-else description="还没有可看的轮次" />
  </el-drawer>
</template>
