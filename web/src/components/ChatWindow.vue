<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { TurnMessage } from '../composables/useChat'
import MessageBubble from './MessageBubble.vue'
import Composer from './Composer.vue'

const props = defineProps<{ messages: TurnMessage[]; busy: boolean }>()
const emit = defineEmits<{
  (e: 'send', value: string): void
  (e: 'trace', message: TurnMessage): void
}>()

const scroller = ref<HTMLElement | null>(null)

/** 空态给的四个问题，刻意覆盖四条不同路径：订单 / 退保 / 理赔 / 转人工。 */
const HINTS = ['订单 A1024 什么状态', '我想退保', '理赔要什么材料', '转人工']

// block 数是增量变化的，用「总 block 数」而不是「消息数」触发滚动，
// 否则流式追加时只在开头滚一次。
const blockTotal = () => props.messages.reduce((n, m) => n + m.blocks.length, 0)

watch([() => props.messages.length, blockTotal], () => nextTick(scrollToBottom))

function scrollToBottom(): void {
  const el = scroller.value
  if (el) el.scrollTop = el.scrollHeight
}
</script>

<template>
  <div class="chat">
    <div ref="scroller" class="chat-scroll">
      <div v-if="messages.length === 0" class="chat-empty">
        <h2>问点什么</h2>
        <p>
          这一版只有规则分流，没有接模型——所以它认得「退保」「A1024」这类明确的说法，
          认不出的会反问你。<br />
          产品、订单、保单、条款全是虚构数据。
        </p>
        <div class="chat-empty-hints">
          <el-button v-for="hint in HINTS" :key="hint" size="small" round @click="emit('send', hint)">
            {{ hint }}
          </el-button>
        </div>
      </div>

      <MessageBubble
        v-for="message in messages"
        :key="message.id"
        :message="message"
        @suggest="(value) => emit('send', value)"
        @trace="(message) => emit('trace', message)"
      />
    </div>

    <Composer :disabled="busy" @send="(value) => emit('send', value)" />
  </div>
</template>
