<script setup lang="ts">
import type { RouteView } from '../types'

defineProps<{ route: RouteView }>()

const STATE_TEXT: Record<string, string> = {
  HIT_RULE: '规则命中',
  HIT_MODEL: '模型判定',
  ASK_BACK: '反问',
  DEGRADED: '降级',
}

const STATE_TYPE: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
  HIT_RULE: 'success',
  HIT_MODEL: 'info',
  ASK_BACK: 'warning',
  DEGRADED: 'danger',
}
</script>

<template>
  <div class="route-badge">
    <el-tag size="small" effect="light" :type="STATE_TYPE[route.state] ?? 'info'">
      {{ STATE_TEXT[route.state] ?? route.state }}
    </el-tag>
    <span class="route-domain">{{ route.domain }}</span>
    <span v-if="route.intent" class="route-intent">{{ route.intent }}</span>
    <span v-if="route.ruleId" class="route-rule">{{ route.ruleId }}</span>
  </div>
</template>
