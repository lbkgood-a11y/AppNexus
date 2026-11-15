<script setup lang="ts">
import { useAttrs } from "vue";
/**
 * 全站分页入口，复用 Element Plus 的国际化与交互。
 * 固定服务端页容量时不展示容量选择；调用者绑定 pageSize 更新事件后才允许切换。
 * 页码从 1 开始，总数必须来自服务端或完整本地结果，不推算未加载数据。
 */
defineOptions({ inheritAttrs: false });
const attrs = useAttrs();
</script>
<template>
  <el-pagination
    v-bind="attrs"
    size="small"
    :page-sizes="(attrs['page-sizes'] as number[] | undefined) ?? [20, 50, 100]"
    :layout="(attrs.layout as string | undefined) ?? (attrs['onUpdate:pageSize'] ? 'total, sizes, prev, pager, next' : 'total, prev, pager, next')"
    class="app-pagination"
  />
</template>
<style scoped>
/* 保留调用方显式布局；窄屏由父区域横向滚动，分页项不收缩，确保页容量和页码完整可操作。 */
.app-pagination { justify-content: flex-end; flex: 0 0 auto; min-width: max-content; }
</style>
<style scoped>
/* 容量文案需要独立宽度，防止 Element Plus 自适应选择器在紧凑分页中仅剩箭头。 */
.app-pagination :deep(.el-pagination__sizes) { flex-shrink: 0; }
.app-pagination :deep(.el-pagination__sizes .el-select) { width: 112px; min-width: 112px; }
</style>
