<script setup lang="ts">
/** T07 卡片网格模板：查询工具和响应式网格解耦，卡片内容与操作由业务 slot 提供。 */
defineSlots<{ header?: () => unknown; toolbar?: () => unknown; default: () => unknown; empty?: () => unknown }>();
</script>
<template><div class="page-template card-grid-template"><slot name="header" /><el-card v-if="$slots.toolbar" class="template-search" shadow="never"><slot name="toolbar" /></el-card><div class="card-grid-template__grid"><slot /></div><slot name="empty" /></div></template>
