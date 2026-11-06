<script setup lang="ts">
import { onBeforeUnmount, onMounted } from "vue";

/** T04 详情表单模板：提供分组、双列内容和固定操作语义，字段校验仍由业务 el-form 定义。 */
const props = withDefaults(defineProps<{ mode?: "create" | "view" | "edit"; dirty?: boolean }>(), { mode: "view", dirty: false });
defineSlots<{ header?: () => unknown; default: () => unknown; actions?: () => unknown; aside?: () => unknown }>();
/** 浏览器离开提醒只在业务显式标记 dirty 时启用；应用内路由提醒由宿主路由守卫补充。 */
function beforeUnload(event: BeforeUnloadEvent) { if (props.dirty) event.preventDefault(); }
onMounted(() => window.addEventListener("beforeunload", beforeUnload));
onBeforeUnmount(() => window.removeEventListener("beforeunload", beforeUnload));
</script>

<template>
  <div class="page-template detail-form-template" :data-mode="mode">
    <slot name="header" />
    <div class="detail-form-template__layout">
      <el-card class="detail-form-template__form" shadow="never"><slot /></el-card>
      <aside v-if="$slots.aside"><slot name="aside" /></aside>
    </div>
    <footer v-if="$slots.actions" class="detail-form-template__actions"><slot name="actions" /></footer>
  </div>
</template>
