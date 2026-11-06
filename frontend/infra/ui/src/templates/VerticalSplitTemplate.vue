<script setup lang="ts">
/** T03 上下联动模板：上下区域独立滚动，主区域支持浏览器原生键盘/指针调整高度。 */
withDefaults(defineProps<{ masterHeight?: number; selected?: boolean }>(), { masterHeight: 280, selected: false });
defineSlots<{ header?: () => unknown; master: () => unknown; detail: () => unknown; empty?: () => unknown }>();
</script>

<template>
  <div class="page-template vertical-split-template" :style="{ '--master-panel-height': `${masterHeight}px` }">
    <slot name="header" />
    <el-card class="vertical-split-template__master" shadow="never"><slot name="master" /></el-card>
    <el-card class="vertical-split-template__detail" shadow="never">
      <slot v-if="selected" name="detail" />
      <slot v-else name="empty"><el-empty /></slot>
    </el-card>
  </div>
</template>
