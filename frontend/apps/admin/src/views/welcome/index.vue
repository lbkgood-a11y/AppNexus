<script setup lang="ts">
import { useI18n } from "vue-i18n";
const { t } = useI18n();
</script>

<template>
  <div class="management-page welcome-page">
    <el-card shadow="never"><div class="welcome-heading"><h1>{{t('welcome.title')}}</h1><p>{{t('welcome.description')}}</p></div><el-empty :description="t('welcome.empty')" :image-size="96" /></el-card>
  </div>
</template>

<style scoped>
.welcome-heading h1 { margin: 0; font-size: 20px; font-weight: 600; }
.welcome-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); line-height: 1.6; }
</style>
