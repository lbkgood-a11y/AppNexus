<script setup lang="ts">
/** T06 Tab 多维详情模板：对象摘要与 Tab 内容分离，按需加载和状态保持由业务页控制。 */
defineSlots<{ header?: () => unknown; summary: () => unknown; tabs: () => unknown }>();
</script>
<template><div class="page-template tabbed-detail-template"><slot name="header" /><el-card shadow="never"><slot name="summary" /></el-card><el-card class="tabbed-detail-template__tabs" shadow="never"><slot name="tabs" /></el-card></div></template>
