<script setup lang="ts">
/** T05 左右详情模板：左侧稳定摘要，右侧承载详情、关联数据与操作记录。 */
withDefaults(defineProps<{ /** true 时左右区域共享一个表面，适合个人中心等强关联工作区。 */ unified?: boolean }>(), { unified: false });
defineSlots<{ header?: () => unknown; summary: () => unknown; default: () => unknown }>();
</script>
<template>
  <div class="page-template split-detail-template" :class="{ 'is-unified': unified }">
    <slot name="header" />
    <el-card v-if="unified" class="split-detail-template__surface" shadow="never"><div class="split-detail-template__layout"><aside><slot name="summary" /></aside><main><slot /></main></div></el-card>
    <div v-else class="split-detail-template__layout"><el-card shadow="never"><slot name="summary" /></el-card><el-card shadow="never"><slot /></el-card></div>
  </div>
</template>
