<script setup lang="ts">
/**
 * T06 多维详情模板；主数据样板按页头、分组表单、只读系统信息、明细页签组织。
 * content 用于跨多个区块共享同一 el-form 实例，避免拆分后漏校验；旧 summary 插槽保持原调用方式。
 * 模板不读取业务数据、不提供权限兜底，保存/取消、折叠和字段校验由页面管理。
 */
withDefaults(defineProps<{
  /** 启用客商详情样板的布局密度，默认 false；无专项要求的业务详情应显式启用。 */
  masterData?: boolean;
}>(),{masterData:false});
defineSlots<{header?:()=>unknown;summary?:()=>unknown;content?:()=>unknown;system?:()=>unknown;tabs?:()=>unknown}>();
</script>
<template>
 <div class="page-template tabbed-detail-template" :class="{'master-data-detail':masterData}">
  <slot name="header" />
  <slot v-if="$slots.content" name="content" />
  <el-card v-else shadow="never"><slot name="summary" /></el-card>
  <el-card v-if="$slots.system" class="detail-system" shadow="never"><slot name="system" /></el-card>
  <el-card v-if="$slots.tabs" class="tabbed-detail-template__tabs" shadow="never"><slot name="tabs" /></el-card>
 </div>
</template>
