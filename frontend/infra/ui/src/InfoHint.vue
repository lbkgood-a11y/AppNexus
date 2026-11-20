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
/* 统一压缩提示按钮背景，避免继承普通文字按钮的横向内边距；颜色和焦点沿用 Element Plus。 */
.el-button.info-hint {
  position: relative;
  width: 24px;
  height: 24px;
  min-height: 24px;
  padding: 0;
  vertical-align: middle;
  flex-shrink: 0;
  margin-inline-start: 4px;
}
/* 边框标签行高为 16px，普通 24px 按钮会撑高标签背景并遮挡输入文字。
   仅在两类公共边框表单的标签内采用 16px 高、24px 宽的按钮；标题和普通标签保持原尺寸。
   inline-flex 消除行内按钮的基线留白，避免标签背景再次超出边框预留区域。 */
:is(.business-border-label-form, .detail-border-label-form) .el-form-item__label:has(.info-hint) {
  display: inline-flex;
  align-items: center;
}
:is(.business-border-label-form, .detail-border-label-form) .el-form-item__label .el-button.info-hint {
  height: 16px;
  min-height: 16px;
  line-height: 16px;
}
/* 触屏扩展透明命中区至 44px，保持可见背景紧凑。 */
@media (pointer: coarse) {
  .el-button.info-hint::after { position: absolute; inset: -10px; content: ""; }
  /* 紧凑标签按钮高度减少 8px，上下各补 4px，使触屏命中区仍为 44×44px。 */
  :is(.business-border-label-form, .detail-border-label-form) .el-form-item__label .el-button.info-hint::after {
    inset: -14px -10px;
  }
}
.info-hint-title { display: inline-flex; align-items: center; gap: 4px; }
</style>
