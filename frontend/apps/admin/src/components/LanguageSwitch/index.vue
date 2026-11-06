<script setup lang="ts">
import { AppIcon } from "@appnexus/icons";
import { useTranslationLang } from "@/layout/hooks/useTranslationLang";
const { t, locale, translationCh, translationEn } = useTranslationLang();
/** 复用模板语言切换入口；仅改变展示偏好，不创建独立语言存储。 */
function selectLanguage(value: string) {
  if (value === "zh") translationCh(); else translationEn();
}
</script>
<template>
  <el-dropdown trigger="click" @command="selectLanguage">
    <el-button text class="platform-toolbar-button" :aria-label="t('platform.language')" :title="t('platform.language')">
      <AppIcon name="globe" :size="18" />
    </el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="zh" :disabled="locale === 'zh'">简体中文</el-dropdown-item>
        <el-dropdown-item command="en" :disabled="locale === 'en'">English</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>
