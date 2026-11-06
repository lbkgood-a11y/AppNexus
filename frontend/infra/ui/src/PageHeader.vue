<script setup lang="ts">
/**
 * 管理页面标题区：统一面包屑、标题、说明、状态与右侧操作，不感知路由或权限。
 * 面包屑由业务页面传入已翻译文本，链接跳转仍由应用壳负责。
 */
defineProps<{
  /** 页面唯一主标题。 */
  title: string;
  /** 页面职责与业务边界说明；为空时不占据空间。 */
  description?: string;
  /** 已按当前语言翻译的面包屑文本，最后一项表示当前页。 */
  breadcrumbs?: string[];
}>();
defineSlots<{ actions?: () => unknown; meta?: () => unknown }>();
</script>

<template>
  <header class="page-header">
    <el-breadcrumb v-if="breadcrumbs?.length" separator="/" class="page-header__breadcrumb" aria-label="Breadcrumb">
      <el-breadcrumb-item v-for="item in breadcrumbs" :key="item">{{ item }}</el-breadcrumb-item>
    </el-breadcrumb>
    <div class="page-header__main">
      <div class="page-header__copy">
        <div class="page-header__title-row"><h1>{{ title }}</h1><slot name="meta" /></div>
        <p v-if="description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="page-header__actions"><slot name="actions" /></div>
    </div>
  </header>
</template>
