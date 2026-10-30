<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{ disabled: boolean }>()
const emit = defineEmits<{ (e: 'send', value: string): void }>()

const text = ref('')
const QUICK = ['订单 A1024 什么状态', '我想退保', '理赔要什么材料', '转人工']

function submit(): void {
  if (props.disabled) return
  const value = text.value.trim()
  if (!value) return
  emit('send', value)
  text.value = ''
}

function onEnter(event: KeyboardEvent): void {
  // Shift+Enter 换行，Enter 直接发；输入法组字期间不拦（event.isComposing）
  if (event.shiftKey || event.isComposing) return
  event.preventDefault()
  submit()
}
</script>

<template>
  <div class="composer">
    <div class="composer-quick">
      <el-button
        v-for="quick in QUICK"
        :key="quick"
        size="small"
        text
        :disabled="disabled"
        @click="emit('send', quick)"
      >
        {{ quick }}
      </el-button>
    </div>

    <div class="composer-row">
      <el-input
        v-model="text"
        type="textarea"
        :rows="2"
        resize="none"
        :disabled="disabled"
        placeholder="问点什么…（Enter 发送，Shift+Enter 换行）"
        @keydown.enter="onEnter"
      />
      <el-button type="primary" :loading="disabled" @click="submit">发送</el-button>
    </div>
  </div>
</template>
