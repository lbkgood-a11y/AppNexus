<script setup lang="ts">
/** T08 数据概览模板：图表组件由宿主按已有依赖注入，模板不绑定图表库或数据源。 */
defineSlots<{ header?: () => unknown; filters?: () => unknown; statistics: () => unknown; charts: () => unknown; table?: () => unknown }>();
</script>
<template><div class="page-template dashboard-template"><slot name="header" /><el-card v-if="$slots.filters" class="template-search" shadow="never"><slot name="filters" /></el-card><section class="dashboard-template__statistics"><slot name="statistics" /></section><section class="dashboard-template__charts"><slot name="charts" /></section><el-card v-if="$slots.table" shadow="never"><slot name="table" /></el-card></div></template>
