<script setup lang="ts">
import { computed } from 'vue'
import type { BlockView } from '../types'

const props = defineProps<{ block: BlockView }>()
defineEmits<{ (e: 'suggest', value: string): void }>()

/**
 * 卡片字段的中文名。后端卡片的 data 是开放 Map（前端不该逼后端为渲染改结构），
 * 所以这里做「认识的翻译、不认识的照原样显示」——多一个字段不会让界面崩，只会多一行。
 */
const CARD_LABELS: Record<string, string> = {
  product: '产品',
  premium: '保费',
  coverage: '保障范围',
  orderNo: '订单号',
  status: '状态',
  paidAt: '支付时间',
  policyNo: '保单号',
  coolingOffDays: '犹豫期（天）',
  citation: '依据',
  next: '下一步',
}

const rows = computed(() =>
  Object.entries(props.block.data ?? {}).map(([key, value]) => ({
    key,
    label: CARD_LABELS[key] ?? key,
    value: format(value),
  })),
)

function format(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (Array.isArray(value)) return value.map(format).join('、')
  return String(value)
}
</script>

<template>
  <p v-if="block.kind === 'text'" class="blk-text">{{ block.text }}</p>

  <div v-else-if="block.kind === 'card'" class="blk-card">
    <div class="blk-card-head">{{ block.cardType }}</div>
    <dl class="blk-card-rows">
      <template v-for="row in rows" :key="row.key">
        <dt>{{ row.label }}</dt>
        <dd>{{ row.value }}</dd>
      </template>
    </dl>
  </div>

  <div v-else-if="block.kind === 'suggestion'" class="blk-suggest">
    <el-button
      v-for="option in block.options ?? []"
      :key="option"
      size="small"
      round
      @click="$emit('suggest', option)"
    >
      {{ option }}
    </el-button>
  </div>

  <p v-else-if="block.kind === 'thinking'" class="blk-think">{{ block.text }}</p>

  <el-alert
    v-else-if="block.kind === 'handoff'"
    class="blk-handoff"
    type="warning"
    :closable="false"
    show-icon
    :title="block.queue ? `已转人工 · ${block.queue}` : '已转人工'"
    :description="block.summary ?? ''"
  />

  <!-- hidden 不上屏：它是给前端算状态用的（比如下一步动作），不是给人看的 -->
</template>
