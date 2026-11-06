<script setup lang="ts">
import { computed, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useMediaQuery } from "@vueuse/core";
import { useGlobal } from "@pureadmin/utils";
import { AppIcon, type PlatformIconName } from "@appnexus/icons";
import { useDataThemeChange } from "@/layout/hooks/useDataThemeChange";

type ThemeMode = "light" | "dark" | "system";
const { t } = useI18n();
const { $storage } = useGlobal<GlobalPropertiesApi>();
const { dataTheme, dataThemeChange } = useDataThemeChange();
/** 系统深色偏好由媒体查询提供；仅在选择 system 时影响当前主题。 */
const prefersDark = useMediaQuery("(prefers-color-scheme: dark)");
const mode = computed<ThemeMode>(() => ($storage.layout?.themeMode as ThemeMode) ?? "system");
const options: { mode: ThemeMode; icon: PlatformIconName; key: string }[] = [
  { mode: "light", icon: "sun", key: "platform.themeLight" },
  { mode: "dark", icon: "moon", key: "platform.themeDark" },
  { mode: "system", icon: "monitor", key: "platform.themeSystem" }
];

/**
 * 应用主题并通过模板现有入口保存，保证登录页、导航栏与设置面板使用同一份偏好。
 * @param value light 为浅色、dark 为深色、system 跟随操作系统；仅保存展示偏好，不涉及会话身份。
 */
function setMode(value: ThemeMode) {
  dataTheme.value = value === "system" ? prefersDark.value : value === "dark";
  dataThemeChange(value);
}
watch([mode, prefersDark], ([value]) => setMode(value), { immediate: true });
</script>

<template>
  <el-dropdown trigger="click" @command="setMode">
    <el-button text class="platform-toolbar-button" :aria-label="t('platform.theme')" :title="t('platform.theme')">
      <AppIcon :name="options.find(option => option.mode === mode)?.icon ?? 'sun'" :size="18" />
    </el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-for="option in options" :key="option.mode" :command="option.mode" :class="{ 'is-selected': mode === option.mode }">
          <AppIcon :name="option.icon" :size="16" class="mr-2" />
          <span>{{ t(option.key) }}</span>
          <AppIcon v-if="mode === option.mode" name="check" :size="16" class="theme-option-check" />
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.theme-option-check { margin-left: auto; color: var(--el-color-primary); }
:deep(.is-selected) { color: var(--el-color-primary); font-weight: 500; }
</style>
