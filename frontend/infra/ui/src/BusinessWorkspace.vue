<script setup lang="ts">
/**
 * 业务工作区将导航、查询和数据管理收束到同一业务表面，强化当前对象的归属关系。
 * 组件只负责视觉层级和语义区域；Tab 状态、查询条件、权限与数据请求均由业务页面管理。
 */
defineProps<{
  /** 当前工作区业务名称，例如“租户管理”；用于区域无障碍名称。 */
  title: string;
  /** 当前业务视图说明，可随 Tab 同步变化；为空时不占据空间。 */
  description?: string;
}>();
defineSlots<{
  navigation?: () => unknown;
  context?: () => unknown;
  feedback?: () => unknown;
  search?: () => unknown;
  data: () => unknown;
}>();
</script>

<template>
  <section class="business-workspace" :aria-label="title" :aria-description="description">
    <header v-if="$slots.navigation || $slots.context" class="business-workspace__navigation">
      <div v-if="$slots.context" class="business-workspace__context">
        <slot name="context" />
      </div>
      <slot name="navigation" />
    </header>
    <div v-if="$slots.feedback" class="business-workspace__feedback"><slot name="feedback" /></div>
    <section v-if="$slots.search" class="business-workspace__search"><slot name="search" /></section>
    <section class="business-workspace__data"><slot name="data" /></section>
  </section>
</template>
