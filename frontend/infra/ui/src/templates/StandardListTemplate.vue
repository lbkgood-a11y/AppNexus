<script setup lang="ts">
/** T01 标准列表模板：只规定标题、查询、工具栏、表格与分页的层级。 */
withDefaults(defineProps<{
  /** 查询区是否展示；由业务页保存筛选状态。 */ filterExpanded?: boolean;
  /** 数据区是否生成 el-card；复用已有卡片型增强表格时设为 false，避免双层容器。 */ dataCard?: boolean;
}>(), { filterExpanded: true, dataCard: true });
defineSlots<{ header?: () => unknown; search?: () => unknown; toolbar?: () => unknown; table: () => unknown; pagination?: () => unknown; feedback?: () => unknown }>();
</script>

<template>
  <div class="page-template standard-list-template">
    <slot name="header" />
    <slot name="feedback" />
    <el-card v-if="$slots.search" class="template-search" shadow="never"><slot name="search" /></el-card>
    <component :is="dataCard ? 'el-card' : 'div'" class="template-data" shadow="never">
      <div v-if="$slots.toolbar" class="template-toolbar"><slot name="toolbar" /></div>
      <div class="template-table"><slot name="table" /></div>
      <div v-if="$slots.pagination" class="template-pagination"><slot name="pagination" /></div>
    </component>
  </div>
</template>
