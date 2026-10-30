<script setup lang="ts">
import { computed } from 'vue'
import type { TurnMessage } from '../composables/useChat'
import BlockRenderer from './BlockRenderer.vue'
import RouteBadge from './RouteBadge.vue'

const props = defineProps<{ message: TurnMessage }>()
const emit = defineEmits<{
  (e: 'suggest', value: string): void
  (e: 'trace', message: TurnMessage): void
}>()

const tokenText = computed(() => {
  const usage = props.message.usage
  if (!usage) return ''
  const total = usage.promptTokens + usage.completionTokens
  return total > 0 ? `${total} tokens` : '未调用模型'
})

const elapsedText = computed(() => {
  const spans = props.message.spans
  if (spans.length === 0) return ''
  return `${spans.reduce((n, s) => n + s.durationMs, 0)} ms`
})
</script>

<template>
  <div class="msg" :class="message.role">
    <div class="msg-body">
      <template v-if="message.role === 'user'">
        <div class="bubble-user">{{ message.text }}</div>
      </template>

      <template v-else>
        <RouteBadge v-if="message.route" :route="message.route" />

        <div class="bubble-bot">
          <span v-if="message.blocks.length === 0 && message.streaming" class="typing">
            <i /><i /><i />
          </span>

          <BlockRenderer
            v-for="(block, index) in message.blocks"
            :key="index"
            :block="block"
            @suggest="(value) => emit('suggest', value)"
          />

          <el-alert
            v-if="message.error"
            :title="message.error"
            type="error"
            :closable="false"
            show-icon
          />
        </div>

        <div v-if="!message.streaming && message.traceId" class="msg-foot">
          <button class="link" @click="emit('trace', message)">
            trace {{ message.traceId }} · {{ message.spans.length }} span
            <template v-if="elapsedText"> · {{ elapsedText }}</template>
            <template v-if="tokenText"> · {{ tokenText }}</template>
          </button>
        </div>
      </template>
    </div>
  </div>
</template>
