<script setup lang="ts">
import { AppIcon } from "@appnexus/icons";
/** 静态使用说明的统一入口；调用者须放在对应标题、字段 label 或列表 titleHelp 插槽中，禁止独占空白行；点击或键盘激活后查看，不承载错误、风险警告及操作结果。 */
defineProps<{
  /** 已国际化的完整说明文字；同时作为按钮的无障碍名称，无默认值。 */
  content: string;
}>();
</script>

<template>
  <el-popover trigger="click" placement="bottom-start" popper-class="app-info-hint" :width="360" :content="content">
    <template #reference>
      <el-button text class="info-hint" :aria-label="content"><AppIcon name="information" :size="18" /></el-button>
    </template>
  </el-popover>
</template>

<style>
/* 手机端限制弹层宽度；使用主题原生文字与背景，长说明自然换行。 */
.app-info-hint { max-width: calc(100vw - 32px); overflow-wrap: anywhere; }
.info-hint { vertical-align: middle; flex-shrink: 0; margin-inline-start: 4px; }
.info-hint-title { display: inline-flex; align-items: center; gap: 4px; }
</style>
