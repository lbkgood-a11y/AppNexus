<script setup lang="ts">
import { ElResult } from "element-plus";
import type { LoadIssue } from "./errorFeedback";

/** 在数据区域复用 Element Plus 结果状态，统一无权样式及重试插槽的展示条件。 */
defineProps<{ issue: LoadIssue }>();
defineSlots<{ retry?: () => unknown }>();
</script>

<template>
  <ElResult :icon="issue.retryable ? 'error' : 'warning'" :title="issue.message">
    <template v-if="issue.retryable && $slots.retry" #extra><slot name="retry" /></template>
  </ElResult>
</template>
